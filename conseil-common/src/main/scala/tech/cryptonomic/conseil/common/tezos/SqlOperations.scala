package tech.cryptonomic.conseil.common.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.generic.chain.{DataOperations, MetadataOperations}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{
  Field,
  FormattedField,
  Predicate,
  Query,
  QueryResponse,
  SimpleField
}

import scala.concurrent.{ExecutionContext, Future}

object SqlOperations {
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
trait SqlOperations extends DataOperations with MetadataOperations {
  import SqlOperations._

  def dbReadHandle: Database

  /**
    * @see `MetadataOperations#runQuery`
    */
  override def runQuery[A](action: DBIO[A]): Future[A] =
    dbReadHandle.run(action)

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
