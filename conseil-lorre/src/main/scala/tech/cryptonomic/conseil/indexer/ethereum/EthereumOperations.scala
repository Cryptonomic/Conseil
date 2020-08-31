package tech.cryptonomic.conseil.indexer.ethereum

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.config.Platforms.EthereumBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumClient
import tech.cryptonomic.conseil.indexer.config.{Custom, Depth, Everything, Newest}
import tech.cryptonomic.conseil.common.ethereum.domain.{Bytecode, Contract}

/**
  * Ethereum operations for Lorre.
  *
  * @param ethereumClient JSON-RPC client to communicate with the Ethereum node
  * @param persistence DB persistence methods for the Ethereum blockchain
  * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
  * @param batchConf Configuration containing batch fetch values
  */
class EthereumOperations[F[_]: Concurrent](
    ethereumClient: EthereumClient[F],
    persistence: EthereumPersistence[F],
    tx: Transactor[F],
    batchConf: EthereumBatchFetchConfiguration
) extends LazyLogging {

  /**
    * Start Lorre with mode defined with [[Depth]].
    *
    * @param depth Can be: Newest, Everything or Custom
    */
  def loadBlocksAndLogs(depth: Depth): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getLatestIndexedBlock))
      .zip(ethereumClient.getMostRecentBlockNumber.map(Integer.decode))
      .flatMap {
        case (latestIndexedBlock, mostRecentBlockNumber) =>
          val range = depth match {
            case Newest => latestIndexedBlock.map(_.number + 1).getOrElse(1) to mostRecentBlockNumber
            case Everything => 1 to mostRecentBlockNumber
            case Custom(depth) if depth > mostRecentBlockNumber && latestIndexedBlock.isEmpty =>
              1 to mostRecentBlockNumber
            case Custom(depth) if depth > mostRecentBlockNumber && latestIndexedBlock.nonEmpty =>
              latestIndexedBlock.map(_.number + 1).getOrElse(1) to mostRecentBlockNumber
            case Custom(depth) => (mostRecentBlockNumber - depth) to mostRecentBlockNumber
          }

          loadBlocksWithTransactions(range) ++ extractTokens(range)
      }

  /**
    * Get blocks from Ethereum node through Ethereum client and save them into the database using Slick.
    * In the beginning, the current list of blocks is obtained from the database and removed from the computation.
    *
    * @param range Inclusive range of the block's height
    */
  def loadBlocksWithTransactions(range: Range.Inclusive): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getIndexedBlockHeights(range)))
      .flatMap(
        existingBlocks =>
          Stream
            .range(range.start, range.end)
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
                  .through(ethereumClient.getTransactionRecipt)
                  .chunkN(Integer.MAX_VALUE)
                  .map(recipts => (block, txs, recipts.toList))
              case (block, txs) => Stream.emit((block, Nil, Nil))
            }
            .evalTap { // log every 10 block
              case (block, txs, recipts) if Integer.decode(block.number) % 10 == 0 =>
                Concurrent[F].delay(
                  logger.info(
                    s"Save block with height: ${block.number} txs: ${txs.size} logs: ${recipts.map(_.logs.size).sum}"
                  )
                )
              case _ => Concurrent[F].unit
            }
            .evalTap {
              case (block, txs, recipts) =>
                tx.transact(persistence.createBlock(block, txs, recipts))
            }
            .flatMap {
              case (block, txs, recipts) =>
                Stream
                  .emits(recipts.filter(_.contractAddress.isDefined))
                  .through(ethereumClient.getContract(batchConf.contractsBatchSize))
                  .chunkN(Integer.MAX_VALUE)
                  .evalTap(contracts => tx.transact(persistence.createContracts(contracts.toList)))
            }
      ) 
      .drain

  /**
    * Get tokens created in the given block number range.
    *
    * @param range Inclusive range of the block's height
    */
  def extractTokens(range: Range.Inclusive): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getContracts(range)))
      .flatMap(Stream.emits)
      .map(
        row =>
          Contract(
            address = row.address,
            blockHash = row.blockHash,
            blockNumber = s"0x${row.blockNumber.toHexString}",
            isErc20 = row.isErc20,
            isErc721 = row.isErc721,
            bytecode = Bytecode(row.bytecode)
          )
      )
      .through(ethereumClient.getTokenInfo)
      .evalTap(token => Concurrent[F].delay(logger.info(s"Save token: ${token.name}")))
      .chunkN(batchConf.tokensBatchSize)
      .evalTap(tokens => tx.transact(persistence.createTokens(tokens.toList)))
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
  def resource[F[_]: Concurrent](
      rpcClient: RpcClient[F],
      tx: Transactor[F],
      batchConf: EthereumBatchFetchConfiguration
  ): Resource[F, EthereumOperations[F]] =
    for {
      ethereumClient <- EthereumClient.resource(rpcClient)
      persistence <- EthereumPersistence.resource
    } yield new EthereumOperations[F](ethereumClient, persistence, tx, batchConf)
}
