package tech.cryptonomic.conseil.api.routes.platform.data

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.generic.chain.DataOperations
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Field, Predicate, Query, QueryResponse, _}
import tech.cryptonomic.conseil.api.routes.platform.Sanitizer._
import tech.cryptonomic.conseil.common.sql.DatabaseMetadataOperations
import tech.cryptonomic.conseil.common.util.DatabaseUtil.QueryBuilder._

import scala.concurrent.{ExecutionContext, Future}

object ApiDataOperations {

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

/** Provides the implementation for `DataOperations` trait */
trait ApiDataOperations extends DatabaseMetadataOperations with DataOperations {
  import ApiDataOperations._

  /** Executes the query with given predicates
    *
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a map
    * */
  override def queryWithPredicates(prefix: String, tableName: String, query: Query)(
      implicit ec: ExecutionContext
  ): Future[List[QueryResponse]] =
    runQuery(
      selectWithPredicates(
        prefix,
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

  /**
    * Selects elements filtered by the predicates
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param columns        list of column names
    * @param predicates     list of predicates for query to be filtered with
    * @param ordering       list of ordering conditions for the query
    * @param aggregation    optional aggregation
    * @param limit          max number of rows fetched
    * @return               list of map of [string, any], which represents list of rows as a map of column name to value
    */
  def selectWithPredicates(
      prefix: String,
      table: String,
      columns: List[Field],
      predicates: List[Predicate],
      ordering: List[QueryOrdering],
      aggregation: List[Aggregation],
      temporalPartition: Option[String],
      snapshot: Option[Snapshot],
      outputType: OutputType,
      limit: Int
  )(implicit ec: ExecutionContext): DBIO[List[QueryResponse]] = {
    val tableWithPrefix = prefix + "." + table
    val q = (temporalPartition, snapshot) match {
      case (Some(tempPartition), Some(snap)) =>
        makeTemporalQuery(tableWithPrefix, columns, predicates, aggregation, ordering, tempPartition, snap, limit)
      case _ =>
        makeQuery(tableWithPrefix, columns, aggregation)
          .addPredicates(predicates)
          .addGroupBy(aggregation, columns)
          .addHaving(aggregation)
          .addOrdering(ordering)
          .addLimit(limit)
    }

    if (outputType == OutputType.sql) {
      DBIO.successful(List(Map("sql" -> Some(q.queryParts.mkString("")))))
    } else {
      q.as[QueryResponse].map(_.toList)
    }
  }

}
