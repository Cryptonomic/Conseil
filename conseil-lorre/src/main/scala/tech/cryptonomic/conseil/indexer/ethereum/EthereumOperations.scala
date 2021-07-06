package tech.cryptonomic.conseil.indexer.ethereum

import java.util.concurrent.ExecutorService

import cats.Functor
import cats.effect.{Concurrent, ContextShift, IO, Resource}
import cats.implicits.catsSyntaxApplicativeId
import fs2.{INothing, Stream}
import slick.jdbc.PostgresProfile.backend.Database
import slickeffect.Transactor
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.config.Platforms.EthereumBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumClient
import tech.cryptonomic.conseil.indexer.config.{Custom, Depth, Everything, Newest}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Ethereum operations for Lorre.
  *
  * @param ethereumClient JSON-RPC client to communicate with the Ethereum node
  * @param persistence DB persistence methods for the Ethereum blockchain
  * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
  * @param batchConf Configuration containing batch fetch values
  */
class EthereumOperations(
    ethereumClient: EthereumClient[IO],
    persistence: EthereumPersistence[IO],
    batchConf: EthereumBatchFetchConfiguration,
    ec: ExecutionContext,
    db: Database
) extends ConseilLogSupport {

  private implicit val eec: ExecutionContext = ec
  private implicit val cs: ContextShift[cats.effect.IO] = IO.contextShift(ec)

  /**
    * SHA-3 signature for: Transfer(address,address,uint256)
    */
  private val tokenTransferSignature = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

  /**
    * Start Lorre with mode defined with [[Depth]].
    *
    * @param depth Can be: Newest, Everything or Custom
    */
  def loadBlocksAndLogs(depth: Depth, headHash: Option[String]) =
    Stream
      .eval{
        IO.fromFuture(IO(db.run(persistence.getLatestIndexedBlock).map { x =>
        db.close()
        x
      }))
      }
      .flatMap {
        case latest if headHash.isDefined =>
          Stream(latest)
            .zip(ethereumClient.getBlockByHash(headHash.get).map(block => Integer.decode(block.number)))
        case latest =>
          Stream(latest)
            .zip(ethereumClient.getMostRecentBlockNumber.map(Integer.decode))

      }
      .flatMap {
        case (latestIndexedBlock, mostRecentBlockNumber) =>
          val range = depth match {
            case Newest => latestIndexedBlock.map(_.level + 1).getOrElse(1) to mostRecentBlockNumber
            case Everything => 1 to mostRecentBlockNumber
            case Custom(depth) if depth > mostRecentBlockNumber && latestIndexedBlock.isEmpty =>
              1 to mostRecentBlockNumber
            case Custom(depth) if depth > mostRecentBlockNumber && latestIndexedBlock.nonEmpty =>
              latestIndexedBlock.map(_.level + 1).getOrElse(1) to mostRecentBlockNumber
            case Custom(depth) => (mostRecentBlockNumber - depth) to mostRecentBlockNumber
          }

          loadBlocksWithTransactions(range)
      }

  /**
    * Get blocks from Ethereum node through Ethereum client and save them into the database using Slick.
    * In the beginning, the current list of blocks is obtained from the database and removed from the computation.
    *
    * @param range Inclusive range of the block's height
    */
  def loadBlocksWithTransactions(range: Range.Inclusive) =
    Stream
      .eval {
        IO.fromFuture(IO(db.run(persistence.getIndexedBlockHeights(range)).map { x =>
          db.close()
          x
        }))
      }
//      .evalTap(
//        xx =>
//          Concurrent[IO].delay(
//            logger.info(
//              s"Block range: $range"
//            )
//          )
//      )
      .flatMap(
        existingBlocks =>
          Stream
            .range(range.start, range.end + 1)
            .filter(height => !existingBlocks.contains(height))
            .map(n => s"0x${n.toHexString}")
            .through(ethereumClient.getBlockByNumber(batchConf.blocksBatchSize))
            .flatMap {
              case block if block.transactions.size > 0 =>
                Stream
                  .emit(block)
                  .through(ethereumClient.getTransactions(batchConf.transactionsBatchSize))
                  .chunkN(Integer.MAX_VALUE)
                  .map(txs => (block, txs.toList))
              case block => Stream.emit((block, Nil))
            }
            .flatMap {
              case (block, txs) if block.transactions.size > 0 =>
                Stream
                  .emits(txs)
                  .through(ethereumClient.getTransactionReceipt(batchConf.transactionsBatchSize))
                  .chunkN(Integer.MAX_VALUE)
                  .map(receipts => (block, txs, receipts.toList, receipts.toList.flatMap(_.logs)))
              case (block, txs) => Stream.emit((block, txs, Nil, Nil))
            }
            .flatMap {
              case (block, txs, receipts, logs) if receipts.exists(_.contractAddress.isDefined) =>
                Stream
                  .emits(receipts)
                  .filter(_.contractAddress.isDefined)
                  .through(ethereumClient.getContract(batchConf.contractsBatchSize))
                  .through(ethereumClient.getContractBalance(block))
                  .through(ethereumClient.addTokenInfo)
                  .chunkN(Integer.MAX_VALUE)
                  .evalTap{accounts => IO.fromFuture(IO(db.run(persistence.createContractAccounts(accounts.toList)).map { x =>
                    db.close()
                    x
                  }))
                  }
                  .evalTap{
                    accounts => IO.fromFuture(IO(db.run(persistence.createAccountBalances(accounts.toList)).map { x =>
                      db.close()
                      x
                    }))
                  }
                  .map(_ => (block, txs, receipts, logs))
              case (block, txs, receipts, logs) => Stream.emit((block, txs, receipts, logs))
            }
            .flatMap {
              case (block, txs, receipts, logs) if txs.size > 0 =>
                Stream
                  .emits(txs)
                  .filter(t => List(Some(t.from), t.to).forall(addr => !receipts.map(_.contractAddress).contains(addr)))
                  .through(ethereumClient.getAccountBalance(block))
                  .chunkN(Integer.MAX_VALUE)
                  .evalTap(
                    accounts =>
                      IO.fromFuture(IO(db.run(persistence.upsertAccounts(accounts.toList.distinct)(ec)).map { x =>
                        db.close()
                        x
                      }))
                  )
                  .evalTap{
                    accounts => IO.fromFuture(IO(db.run(persistence.createAccountBalances(accounts.toList.distinct)).map { x =>
                      db.close()
                      x
                    }))
                  }
                  .map(_ => (block, txs, receipts, logs))
              case (block, txs, receipts, logs) => Stream.emit((block, txs, receipts, logs))
            }
            .flatMap {
              case (block, txs, receipts, logs)
                  if logs.size > 0 && logs
                      .exists(log => log.topics.size == 3 && log.topics.contains(tokenTransferSignature)) =>
                Stream
                  .emits(logs)
                  .filter(log => log.topics.size == 3 && log.topics.contains(tokenTransferSignature))
                  .through(ethereumClient.getTokenTransfer(block))
                  .chunkN(Integer.MAX_VALUE)
                  .evalTap{ tokenTransfers =>

                    IO.fromFuture(IO(db.run(persistence.createTokenTransfers(tokenTransfers.toList)).map { x =>
                      db.close()
                      x
                    }))
                  }
                  .map(tokenTransfers => (block, txs, receipts, tokenTransfers.toList))
              case (block, txs, receipts, _) => Stream.emit((block, txs, receipts, Nil))
            }
            .flatMap {
              case (block, txs, receipts, tokenTransfers) if tokenTransfers.size > 0 =>
                Stream
                  .emits(tokenTransfers)
                  .through(ethereumClient.getTokenBalance(block))
                  .chunkN(Integer.MAX_VALUE)
                  .evalTap { tokenBalances =>
                    IO.fromFuture(IO(db.run(persistence.createTokenBalances(tokenBalances.toList)).map { x =>
                      db.close()
                      x
                    }))
                  }
                  .map(_ => (block, txs, receipts))
              case (block, txs, receipts, _) => Stream.emit((block, txs, receipts))
            }
//            .evalTap {
//              case (block, txs, receipts) if Integer.decode(block.number) % 10 == 0 =>
//                Concurrent[IO].delay(
//                  logger.info(
//                    s"Save block with number: ${block.number} txs: ${txs.size} logs: ${receipts.map(_.logs.size).sum}"
//                  )
//                )
//              case _ => Concurrent[IO].unit
//            }
            .evalTap {
              case (block, txs, receipts) =>
                IO.fromFuture(IO(db.run(persistence.createBlock(block, txs, receipts)).map { x =>
                  db.close()
                  x
                }))
            }
      )
      .drain
}

object EthereumOperations {

  /**
    * Create [[cats.Resource]] with [[EthereumOperations]].
    *
    * @param rpcClient JSON-RPC client to communicate with the Ethereum node
    * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
    * @param batchConf Configuration containing batch fetch values
    */
  def resource(
      rpcClient: RpcClient[cats.effect.IO],
      batchConf: EthereumBatchFetchConfiguration,
      ec: ExecutionContext,
      db: Database
  )(implicit c: Concurrent[cats.effect.IO]): Resource[IO, EthereumOperations] =
    for {
      ethereumClient <- EthereumClient.resource[cats.effect.IO](rpcClient)
      persistence <- EthereumPersistence.resource[cats.effect.IO]
    } yield new EthereumOperations(ethereumClient, persistence, batchConf, ec, db)
}
