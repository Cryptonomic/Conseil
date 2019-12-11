package tech.cryptonomic.conseil.tezos

import com.github.ghik.silencer.silent
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.generic.chain.{DataOperations, MetadataOperations}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{
  Field,
  FormattedField,
  Predicate,
  Query,
  QueryResponse,
  SimpleField
}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}
import tech.cryptonomic.conseil.tezos.Tables.{BlocksRow, VotesRow}

object ApiOperations {
  case class BlockResult(block: Tables.BlocksRow, operation_groups: Seq[Tables.OperationGroupsRow])
  case class OperationGroupResult(operation_group: Tables.OperationGroupsRow, operations: Seq[Tables.OperationsRow])
  case class AccountResult(account: Tables.AccountsRow)

  /** Sanitizes string to be viable to paste into plain SQL */
  def sanitizeForSql(str: String): String = {
    val supportedCharacters = Set('_', '.', '+', ':', '-', ' ')
    str.filter(c => c.isLetterOrDigit || supportedCharacters.contains(c))
  }

  /** Sanitizes datePart aggregate function*/
  def sanitizeDatePartAggregation(str: String): String = {
    val supportedCharacters = Set('Y', 'M', 'D', 'A', '-')
    str.filterNot(c => c.isWhitespace || !supportedCharacters.contains(c))
  }
}

/**
  * Functionality for fetching data from the Conseil database.
  */
class ApiOperations extends DataOperations with MetadataOperations {
  import ApiOperations._
  lazy val dbReadHandle: Database = DatabaseUtil.conseilDb

  /**
    * @see `MetadataOperations#runQuery`
    */
  override def runQuery[A](action: DBIO[A]): Future[A] =
    dbReadHandle.run(action)

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.Blocks.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the max level of baking rights.
    *
    * @return Max level or -1 if no baking rights were found in the database.
    */
  def fetchMaxBakingRightsLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.BakingRights.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the max level of endorsing rights.
    *
    * @return Max level or -1 if no endorsing rights were found in the database.
    */
  def fetchMaxEndorsingRightsLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.EndorsingRights.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the level of the most recent votes stored in the database.
    *
    * @return Max level or -1 if no votes were found in the database.
    */
  def fetchVotesMaxLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.Votes.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  def fetchVotesAtLevel(level: Int)(implicit ec: ExecutionContext): Future[List[VotesRow]] =
    runQuery(Tables.Votes.filter(_.level === level).result).map(_.toList)

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
    * Fetches a block by level from the db
    *
    * @param level the requested level for the block
    * @return the block if that level is already stored
    */
  def fetchBlockAtLevel(level: Int): Future[Option[BlocksRow]] =
    runQuery(Tables.Blocks.filter(_.level === level).result.headOption)

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
      operations <- TezosDatabaseOperations.operationsForGroup(operationGroupHash)
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
  private[tezos] def latestBlockIO()(implicit ec: ExecutionContext): DBIO[Option[Tables.BlocksRow]] =
    TezosDb.fetchMaxBlockLevel.flatMap(
      maxLevel =>
        Tables.Blocks
          .filter(_.level === maxLevel)
          .take(1)
          .result
          .headOption
    )

  /** Executes the query with given predicates
    *
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a map
    * */
  override def queryWithPredicates(tableName: String, query: Query)(
      implicit ec: ExecutionContext
  ): Future[List[QueryResponse]] =
    runQuery(
      TezosDatabaseOperations.selectWithPredicates(
        tableName,
        sanitizeFields(query.fields),
        sanitizePredicates(query.predicates),
        query.orderBy,
        query.aggregation,
        query.temporalPartition,
        query.snapshot,
        query.output,
        query.limit
      )
    )

  /** Sanitizes predicate values so query is safe from SQL injection */
  def sanitizePredicates(predicates: List[Predicate]): List[Predicate] =
    predicates.map { predicate =>
      predicate.copy(set = predicate.set.map(field => sanitizeForSql(field.toString)))
    }

  /** Sanitizes aggregation format so query is safe from SQL injection */
  def sanitizeFields(fields: List[Field]): List[Field] =
    fields.map {
      case SimpleField(field) => SimpleField(sanitizeForSql(field))
      case FormattedField(field, function, format) =>
        FormattedField(sanitizeForSql(field), function, sanitizeDatePartAggregation(format))
    }

}
