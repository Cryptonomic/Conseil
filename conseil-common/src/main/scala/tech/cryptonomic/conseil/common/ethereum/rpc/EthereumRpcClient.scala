package tech.cryptonomic.conseil.common.ethereum.rpc

import cats.effect.{Concurrent, Resource}
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.ethereum.domain.{
  Account,
  Bytecode,
  Contract,
  Token,
  TokenBalance,
  TokenStandards,
  TokenTransfer
}
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumRpcCommands._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.{Block, Log, Transaction, TransactionReceipt}
import tech.cryptonomic.conseil.common.ethereum.Utils

import java.sql.Timestamp
import java.time.Instant

/**
  * Ethereum JSON-RPC client according to the specification at https://eth.wiki/json-rpc/API
  *
  * @param client [[RpcClient]] to use with the Ethereum JSON-RPC api.
  *
  * * Usage example:
  *
  * {{{
  *   import cats.effect.IO
  *
  *   val ethereumClient = new EthereumClient[IO](rpcClient)
  *
  *   // To call [[fs2.Pipe]] methods use:
  *   Stream(1, 2).through(ethereumClient.getBlockByNumber(batchSize = 10)).compile.toList
  *   // The result will be:
  *   val res0: List[Block] = List(block1, block2)
  * }}}
  */
class EthereumClient[F[_]: Concurrent](
    client: RpcClient[F]
) extends ConseilLogSupport {

  private val nullAddress = "0x0000000000000000000000000000000000000000000000000000000000000000"

  /**
    * Get the number of most recent block.
    */
  def getMostRecentBlockNumber: Stream[F, String] =
    Stream(EthBlockNumber.request)
      .through(client.stream[EthBlockNumber.Params.type, String](batchSize = 1))

  /**
    * Get Block by number.
    *
    * @param batchSize The size of the batched request in single HTTP call.
    */
  def getBlockByNumber(batchSize: Int): Pipe[F, String, Block] =
    _.map(EthGetBlockByNumber.request)
      .through(client.stream[EthGetBlockByNumber.Params, Block](batchSize))

  /**
    * Get block's transactions. Call JSON-RPC for each transaction from the given block.
    *
    * @param batchSize The size of the batched request in single HTTP call.
    */
  def getTransactions(batchSize: Int): Pipe[F, Block, Transaction] =
    stream =>
      stream
        .map(_.transactions)
        .flatMap(Stream.emits)
        .map(EthGetTransactionByHash.request)
        .through(client.stream[EthGetTransactionByHash.Params, Transaction](batchSize))

  /**
    * Get transaction receipt.
    */
  def getTransactionReceipt: Pipe[F, Transaction, TransactionReceipt] =
    stream =>
      stream
        .map(_.hash)
        .map(EthGetTransactionReceipt.request)
        .through(client.stream[EthGetTransactionReceipt.Params, TransactionReceipt](batchSize = 10))

  /**
    * Returns contract from a given transaction receipt.
    *
    * @param batchSize The size of the batched request in single HTTP call.
    */
  def getContract(batchSize: Int): Pipe[F, TransactionReceipt, Contract] =
    stream =>
      stream
        .filter(_.contractAddress.isDefined)
        .flatMap { receipt =>
          Stream
            .emit(EthGetCode.request(receipt.contractAddress.get, receipt.blockNumber))
            .through(client.stream[EthGetCode.Params, Bytecode](batchSize))
            .map(
              bytecode =>
                Contract(
                  address = receipt.contractAddress.get,
                  blockHash = receipt.blockHash,
                  blockNumber = receipt.blockNumber,
                  bytecode = bytecode
                )
            )
        }

  /**
    * Get token information from given contract.
    */
  def getTokenInfo: Pipe[F, Contract, Token] =
    stream =>
      stream.flatMap { contract =>
        stream
          .map(
            contract =>
              Seq("name", "symbol", "decimals", "totalSupply")
                .map(f => EthCall.request(contract.blockNumber, contract.address, s"0x${Utils.keccak(s"$f()")}"))
          )
          .flatMap(Stream.emits)
          .through(client.stream[EthCall.Params, String](batchSize = 1))
          .chunkN(4)
          .map(_.toList)
          .collect {
            case name :: symbol :: decimals :: totalSupply :: Nil =>
              Token(
                address = contract.address,
                blockHash = contract.blockHash,
                blockNumber = contract.blockNumber,
                name = Utils.hexToString(name),
                symbol = Utils.hexToString(symbol),
                decimals = decimals,
                totalSupply = totalSupply
              )
          }
      }

  /**
    * Extract token transfers from log
    */
  def getTokenTransfer(block: Block): Pipe[F, Log, TokenTransfer] =
    stream =>
      stream.map { log =>
        TokenTransfer(
          tokenAddress = log.address,
          blockHash = log.blockHash,
          blockNumber = Integer.decode(log.blockNumber),
          timestamp = block.timestamp,
          transactionHash = log.transactionHash,
          logIndex = log.logIndex,
          fromAddress = log.topics(1),
          toAddress = log.topics(2),
          value = Utils.hexStringToBigDecimal(log.data)
        )
      }

  /**
    * Get token balances for at given block number from token transfer
    *
    * @param block Block at which we extract the account token balance
    */
  def getTokenBalance(block: Block): Pipe[F, TokenTransfer, TokenBalance] =
    stream =>
      stream.flatMap { tokenTransfer =>
        Stream
          .emits(Seq(tokenTransfer.fromAddress, tokenTransfer.toAddress))
          .filter(address => address != nullAddress)
          .flatMap { address =>
            Stream
              .emit(
                EthCall.request(
                  s"0x${tokenTransfer.blockNumber.toHexString}",
                  tokenTransfer.tokenAddress,
                  s"0x${Utils.keccak(s"balanceOf(address)")}${address.stripPrefix("0x")}"
                )
              )
              .through(client.stream[EthCall.Params, String](batchSize = 1))
              .map(balance => (address, balance))
          }
          .map {
            case (address, balance) =>
              TokenBalance(
                accountAddress = address,
                blockHash = tokenTransfer.blockHash,
                blockNumber = tokenTransfer.blockNumber,
                transactionHash = tokenTransfer.transactionHash,
                tokenAddress = tokenTransfer.tokenAddress,
                value = Utils.hexStringToBigDecimal(balance),
                asof = Timestamp.from(Instant.ofEpochSecond(Integer.decode(block.timestamp).toLong))
              )
          }

      }

  def getAccountBalance: Pipe[F, Transaction, Account] =
    stream =>
      stream.flatMap { transaction =>
        Stream
          .emits(Seq(Some(transaction.from), transaction.to).flatten)
          .flatMap { address =>
            Stream
              .emit(address)
              .map(EthGetBalance.request)
              .through(client.stream[EthGetBalance.Params, String](batchSize = 1))
              .map(balance => (address, balance))
          }
          .map {
            case (address, balance) =>
              Account(
                address,
                transaction.blockHash,
                transaction.blockNumber,
                "123",
                Utils.hexStringToBigDecimal(balance)
              )
          }
      }

  def getContractBalance: Pipe[F, Contract, Account] =
    stream =>
      stream.flatMap { contract =>
        Stream
          .emit(contract.address)
          .map(EthGetBalance.request)
          .through(client.stream[EthGetBalance.Params, String](batchSize = 1))
          .map { balance =>
            Account(
              contract.address,
              contract.blockHash,
              contract.blockNumber,
              "123",
              Utils.hexStringToBigDecimal(balance),
              bytecode = Some(contract.bytecode),
              tokenStandard = (contract.isErc20, contract.isErc721) match {
                case (true, false) => Some(TokenStandards.ERC20)
                case (false, true) => Some(TokenStandards.ERC721)
                case _ => None
              }
            )
          }
      }

  def addTokenInfo: Pipe[F, Account, Account] =
    stream =>
      stream.flatMap {
        case token if token.tokenStandard.isDefined =>
          Stream
            .emits(Seq("name", "symbol", "decimals", "totalSupply"))
            .map(f => EthCall.request(token.blockNumber, token.address, s"0x${Utils.keccak(s"$f()")}"))
            .through(client.stream[EthCall.Params, String](batchSize = 1))
            .chunkN(4)
            .map(_.toList)
            .collect {
              case name :: symbol :: decimals :: totalSupply :: Nil =>
                token.copy(
                  name = Some(name),
                  symbol = Some(symbol),
                  decimals = Some(decimals),
                  totalSupply = Some(totalSupply)
                )
            }

        case account => Stream.emit(account)
      }
}

object EthereumClient {

  /**
    * Create [[cats.Resource]] with [[EthereumClient]].
    *
    * @param client [[RpcClient]] to use with the EthereumClient JSON-RPC api.
    */
  def resource[F[_]: Concurrent](client: RpcClient[F]): Resource[F, EthereumClient[F]] =
    Resource.pure(new EthereumClient[F](client))
}
