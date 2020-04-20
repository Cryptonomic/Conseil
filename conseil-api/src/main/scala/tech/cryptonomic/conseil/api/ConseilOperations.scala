package tech.cryptonomic.conseil.api

import com.github.ghik.silencer.silent
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.tezos.SqlOperations.{AccountResult, BlockResult, OperationGroupResult}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.common.tezos.{SqlOperations, Tables, TezosDatabaseOperations => TezosDb}

import scala.concurrent.{ExecutionContext, Future}

class ConseilOperations extends SqlOperations {

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
      operations <- TezosDb.operationsForGroup(operationGroupHash)
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
    TezosDb.fetchMaxBlockLevel.flatMap(
      maxLevel =>
        Tables.Blocks
          .filter(_.level === maxLevel)
          .take(1)
          .result
          .headOption
    )

}
