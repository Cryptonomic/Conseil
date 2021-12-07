package tech.cryptonomic.conseil.platform.data.tezos

import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.ApiDataOperations
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OperationType, Predicate}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{AccountId, BlockLevel, TezosBlockHash}
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.CollectionOps._

import scala.concurrent.{ExecutionContext, Future}

object TezosDataOperations {
  case class BlockResult(block: Tables.BlocksRow, operation_groups: Seq[Tables.OperationGroupsRow])
  case class OperationGroupResult(operation_group: Tables.OperationGroupsRow, operations: Seq[Tables.OperationsRow])
  case class AccountResult(account: Tables.AccountsRow)

  import io.circe.generic.semiauto._
  import Tables.blocksRowCodec
  import Tables.operationGroupsRowCodec
  implicit val blockResultCodec = deriveCodec[BlockResult]
  implicit val accountResultCodec = deriveCodec[AccountResult]

  final val InvalidationAwareAttribute = "invalidated_asof"

  /* this is the basic predicate that will remove any row which was fork-invalidated from results */
  private val nonInvalidatedPredicate = Predicate(InvalidationAwareAttribute, OperationType.isnull)
}

class TezosDataOperations(dbConfig: Config) extends ApiDataOperations {
  import TezosDataOperations._

  override protected val forkRelatedFields = Set("invalidated_asof", "fork_id")

  // override
  protected def hideForkResults(userQueryPredicates: List[Predicate]): List[Predicate] = {
    /* each predicate group will need an additional predicate, because the grouping logic will
     * combine them with an OR, thus nullifying the effect of adding only one predicate overall
     */
    val groups = userQueryPredicates.map(_.group).distinct
    if (groups.nonEmpty)
      groups.map(predicateGroup => nonInvalidatedPredicate.copy(group = predicateGroup))
    else
      nonInvalidatedPredicate :: Nil
  }

  override lazy val dbReadHandle: Database = Database.forConfig("", dbConfig)

  /**
    * Fetches the most recent block stored in the database.
    *
    * @return Latest block.
    */
  def fetchLatestBlock()(implicit ec: ExecutionContext): Future[Option[Tables.BlocksRow]] =
    runQuery(latestBlockIO())

  /**
    * Fetches a block by block hash from the db.
    *
    * @param hash The block's hash
    * @return The block along with its operations, if the hash matches anything
    */
  def fetchBlock(hash: TezosBlockHash)(implicit ec: ExecutionContext): Future[Option[BlockResult]] = {
    val joins = for {
      groups <- Tables.OperationGroups if groups.blockId === hash.value && groups.invalidatedAsof.isEmpty
      block <- Tables.Blocks if block.hash === hash.value && block.invalidatedAsof.isEmpty
    } yield (block, groups)

    runQuery(joins.result).map { paired =>
      val (blocks, groups) = paired.unzip
      blocks.headOption.map { block =>
        BlockResult(
          block = block,
          operation_groups = groups
        )
      }
    }
  }

  /**
    * Fetch a given operation group
    *
    * Running the returned operation will fail with `NoSuchElementException` if no block is found on the db
    *
    * @param operationGroupHash Operation group hash
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return Operation group along with associated operations and accounts
    */
  def fetchOperationGroup(
      operationGroupHash: String
  )(implicit ec: ExecutionContext): Future[Option[OperationGroupResult]] = {
    // @silent("parameter value latest in value")
    val groupsMapIO = for {
      latest <- latestBlockIO if latest.nonEmpty
      operations <- operationsForGroup(operationGroupHash)
    } yield
      operations.map {
        case (opGroup, ops) =>
          OperationGroupResult(
            operation_group = opGroup,
            operations = ops
          )
      }

    runQuery(groupsMapIO)
  }

  /**
    * Reads in all operations referring to the group
    * @param groupHash is the group identifier
    * @param ec the `ExecutionContext` needed to compose db operations
    * @return the operations and the collecting group, if there's one for the given hash, else `None`
    */
  private[tezos] def operationsForGroup(
      groupHash: String
  )(implicit ec: ExecutionContext): DBIO[Option[(Tables.OperationGroupsRow, Seq[Tables.OperationsRow])]] =
    (for {
      operation <- Tables.Operations if operation.operationGroupHash === groupHash && operation.invalidatedAsof.isEmpty
      group <- Tables.OperationGroups if group.hash === groupHash && group.invalidatedAsof.isEmpty
    } yield (group, operation)).result.map { pairs =>
      /*
       * we first collect all de-normalized pairs under the common group and then extract the
       * only key-value from the resulting map
       */
      val keyed = pairs.byKey()
      keyed.keys.headOption
        .map(k => (k, keyed(k)))
    }

  /**
    * Fetches an account by account id from the db.
    * @param account_id The account's id number
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return The account
    */
  def fetchAccount(account_id: AccountId)(implicit ec: ExecutionContext): Future[Option[AccountResult]] = {
    val fetchOperation =
      Tables.Accounts
        .filter(row => row.accountId === account_id.value && row.invalidatedAsof.isEmpty)
        .take(1)
        .result

    runQuery(fetchOperation).map { accounts =>
      accounts.headOption.map(AccountResult)
    }
  }

  /**
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return the most recent block, if one exists in the database.
    */
  def latestBlockIO()(implicit ec: ExecutionContext): DBIO[Option[Tables.BlocksRow]] =
    fetchMaxBlockLevel.flatMap(
      maxLevel =>
        Tables.Blocks
          .filter(row => row.level === maxLevel && row.invalidatedAsof.isEmpty)
          .take(1)
          .result
          .headOption
    )

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BlockLevel = -1

  /** Computes the max level of blocks or [[defaultBlockLevel]] if no block exists */
  private[tezos] def fetchMaxBlockLevel: DBIO[BlockLevel] =
    Tables.Blocks
      .filter(_.invalidatedAsof.isEmpty)
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel)
      .result

}
