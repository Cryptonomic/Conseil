package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import com.github.ghik.silencer.silent
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.BlockHash
import tech.cryptonomic.conseil.common.tezos.TezosTypes.AccountId
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.common.util.CollectionOps._

import scala.concurrent.{ExecutionContext, Future}

object TezosDataOperations {
  case class BlockResult(block: Tables.BlocksRow, operation_groups: Seq[Tables.OperationGroupsRow])
  case class OperationGroupResult(operation_group: Tables.OperationGroupsRow, operations: Seq[Tables.OperationsRow])
  case class AccountResult(account: Tables.AccountsRow)
}

class TezosDataOperations extends ApiDataOperations {
  import TezosDataOperations._

  override lazy val dbReadHandle: Database = DatabaseUtil.conseilDb

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
  def fetchBlock(hash: BlockHash)(implicit ec: ExecutionContext): Future[Option[BlockResult]] = {
    val joins = for {
      groups <- Tables.OperationGroups if groups.blockId === hash.value
      block <- groups.blocksFk
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
    @silent("parameter value latest in value")
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

  /** Precompiled fetch for Operations by Group */
  val operationsByGroupHash = Tables.Operations.findBy(_.operationGroupHash)

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
      operation <- operationsByGroupHash(groupHash).extract
      group <- operation.operationGroupsFk
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
        .filter(row => row.accountId === account_id.id)
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
          .filter(_.level === maxLevel)
          .take(1)
          .result
          .headOption
    )

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BigDecimal = -1

  /** Computes the max level of blocks or [[defaultBlockLevel]] if no block exists */
  private[tezos] def fetchMaxBlockLevel: DBIO[Int] =
    Tables.Blocks
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel.toInt)
      .result

}
