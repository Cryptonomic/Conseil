package tech.cryptonomic.conseil.indexer.tezos.bigmaps

import java.sql.Timestamp

import cats.implicits._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables}
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.indexer.tezos.michelson
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.common.util.Conversion
import scribe._

/** Collects specific [[Conversion]] instances to implicitly convert between
  * big-map related entries and things to be used when saving such data
  * on the database.
  */
object BigMapsConversions extends ConseilLogSupport {

  // Simplify understanding in parts of the code
  case class BlockBigMapDiff(get: BlockTagged[(TezosBlockHash, Option[OperationHash], Contract.BigMapDiff)])
      extends AnyVal
  case class BlockContractIdsBigMapDiff(
      get: (TezosBlockHash, List[ContractId], Contract.BigMapDiff, Option[BlockLevel])
  ) extends AnyVal

  //input to collect token data to convert
  case class TokenUpdatesInput(
      block: Block,
      contractUpdates: Map[ContractId, (Option[ParametersCompatibility], List[Contract.BigMapUpdate])]
  )
  //output to token data converted
  case class TokenUpdate(block: Block, tokenContractId: ContractId, accountId: AccountId, balance: BigInt)

  implicit val bigMapDiffToBigMapRow =
    new Conversion[Option, BlockBigMapDiff, Tables.BigMapsRow] {
      import tech.cryptonomic.conseil.common.tezos.TezosTypes.Contract.BigMapAlloc
      import michelson.dto.MichelsonExpression
      import michelson.JsonToMichelson.toMichelsonScript
      import michelson.parser.JsonParser._
      //needed to call the michelson conversion
      implicit lazy val _: Logger = logger

      def convert(from: BlockBigMapDiff) = from.get match {
        case BlockTagged(ref, (_, _, BigMapAlloc(_, Decimal(id), key_type, value_type))) =>
          Some(
            Tables.BigMapsRow(
              bigMapId = id,
              keyType = Some(toMichelsonScript[MichelsonExpression](key_type.expression)),
              valueType = Some(toMichelsonScript[MichelsonExpression](value_type.expression)),
              forkId = Fork.mainForkId,
              blockLevel = Some(ref.level)
            )
          )
        case BlockTagged(ref, (hash, _, BigMapAlloc(_, InvalidDecimal(json), _, _))) =>
          logger.warn(
            s"Big Map Allocations: A big_map_diff allocation hasn't been converted to a BigMap on db, because the map id '$json' is not a valid number. The block containing the Origination operation is ${hash.value}"
          )
          None
        case BlockTagged(ref, (_, opHash, diffAction)) =>
          logger.warn(
            s"Big Map Allocations: A big_map_diff result will be ignored by the allocation conversion to BigMap on db, because the diff action is not supported: $diffAction for operation $opHash"
          )
          None
      }
    }

  /* This will only convert big map updates actually, as the other types of
   * operations are handled differently
   */
  implicit val bigMapDiffToBigMapContentsRow =
    new Conversion[Option, BlockBigMapDiff, Tables.BigMapContentsRow] {
      import tech.cryptonomic.conseil.common.tezos.TezosTypes.Contract.BigMapUpdate
      import michelson.dto.MichelsonInstruction
      import michelson.JsonToMichelson.toMichelsonScript
      import michelson.parser.JsonParser._
      //needed to call the michelson conversion
      implicit lazy val _: Logger = logger

      def convert(from: BlockBigMapDiff) = from.get match {
        case BlockTagged(ref, (_, opGroupHash, BigMapUpdate(_, key, keyHash, Decimal(id), value))) =>
          Some(
            Tables.BigMapContentsRow(
              bigMapId = id,
              key = toMichelsonScript[MichelsonInstruction](
                key.expression
              ), //we're using instructions to represent data values
              keyHash = keyHash.value,
              operationGroupId = opGroupHash.map(_.value),
              value = value.map(it =>
                toMichelsonScript[MichelsonInstruction](it.expression)
              ), //we're using instructions to represent data values
              valueMicheline = value.map(_.expression),
              blockLevel = Some(ref.level),
              timestamp = ref.timestamp.map(Timestamp.from),
              cycle = ref.cycle,
              period = ref.period,
              forkId = Fork.mainForkId
            )
          )
        case BlockTagged(ref, (hash, _, BigMapUpdate(_, _, _, InvalidDecimal(json), _))) =>
          logger.warn(
            s"Big Map Updates: A big_map_diff update hasn't been converted to a BigMapContent on db, because the map id '$json' is not a valid number. The block containing the Transation operation is ${hash.value}"
          )
          None
        case BlockTagged(ref, (_, opHash, diffAction)) =>
          logger.warn(
            s"Big Map Updates: A big_map_diff result will be ignored by the update conversion to BigMapContent on db, because the diff action is not supported: $diffAction for operation $opHash"
          )
          None
      }
    }

  implicit val bigMapDiffToBigMapOriginatedContracts =
    new Conversion[List, BlockContractIdsBigMapDiff, Tables.OriginatedAccountMapsRow] {
      import tech.cryptonomic.conseil.common.tezos.TezosTypes.Contract.BigMapAlloc
      implicit lazy val _ = logger

      def convert(from: BlockContractIdsBigMapDiff) = from.get match {
        case (_, ids, BigMapAlloc(_, Decimal(id), _, _), level) =>
          ids.map(contractId =>
            Tables.OriginatedAccountMapsRow(
              bigMapId = id,
              accountId = contractId.id,
              blockLevel = level,
              forkId = Fork.mainForkId
            )
          )
        case (hash, ids, BigMapAlloc(_, InvalidDecimal(json), _, _), _) =>
          val showIds = ids.mkString(", ")
          logger.warn(
            s"Big Map Origin: A big_map_diff allocation hasn't been converted to a OriginatedAccounts-BigMap association on db, because the map id '$json' is not a valid number. The block containing the Transation operation is ${hash.value}, involving accounts $showIds"
          )
          List.empty
        case (_, opHash, diffAction, _) =>
          logger.warn(
            s"Big Map Origin: A big_map_diff result will be ignored and not be converted to a relation for OriginatedAccounts to BigMap on db, because the diff action is not supported: $diffAction for operation $opHash"
          )
          List.empty
      }
    }

  implicit def contractsToTokenBalanceUpdates(implicit tokenContracts: TokenContracts) =
    new Conversion[List, TokenUpdatesInput, TokenUpdate] {
      def convert(from: TokenUpdatesInput): List[TokenUpdate] = {
        val TokenUpdatesInput(block, contractUpdates) = from

        //we're looking for known token ledgers based on the contract id and the specific map identified by a diff
        val tokenTransactions: List[(ContractId, List[TokenContracts.BalanceUpdate])] = contractUpdates.map {
          case (tokenId, (params, updates)) =>
            val bigMapToTokenTransaction: Contract.BigMapUpdate => Option[TokenContracts.BalanceUpdate] =
              tokenContracts.readBalance(tokenId)(_, params)
            val tokenUpdates = updates.map(bigMapToTokenTransaction).flattenOption
            tokenId -> tokenUpdates
        }.toList

        if (contractUpdates.nonEmpty) {
          logger.info(
            s"""A known token contract was invoked, I will convert updates to database rows
              |Updates to big maps: $contractUpdates
              |Token balance changes to store: $tokenTransactions""".stripMargin
          )
        }

        for {
          (tokenId, balanceChanges) <- tokenTransactions
          (accountId, newBalance) <- balanceChanges
        } yield TokenUpdate(block, tokenId, accountId, newBalance)

      }
    }
}
