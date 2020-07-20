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
    /* we add [pre/post]-processing of the queries to take into
     * account the need to hide any fork-invalidated data
     */
    val tableWithPrefix = prefix + "." + table
    val effectiveColumns = columns.filterNot(hiddenForkFields)
    val effectiveAggregations = aggregation.filterNot(hiddenForkFields)
    val effectiveOrdering = ordering.filterNot(hiddenForkFields)
    val effectivePredicates = hideForkResults(predicates) ++ predicates
    val q = (temporalPartition, snapshot) match {
      case (Some(tempPartition), Some(snap)) =>
        makeTemporalQuery(
          tableWithPrefix,
          effectiveColumns,
          effectivePredicates,
          effectiveAggregations,
          effectiveOrdering,
          tempPartition,
          snap,
          limit
        )
      case _ =>
        makeQuery(tableWithPrefix, effectiveColumns, effectiveAggregations)
          .addPredicates(effectivePredicates)
          .addGroupBy(effectiveAggregations, effectiveColumns)
          .addHaving(effectiveAggregations)
          .addOrdering(effectiveOrdering)
          .addLimit(limit)
    }

    if (outputType == OutputType.sql) {
      DBIO.successful(List(Map("sql" -> Some(q.queryParts.mkString("")))))
    } else {
      q.as[QueryResponse].map(_.toList.map(hideForkFields))
    }
  }

  /** We collect here field names that only relates to the chain fork handling process.
    * As such, these fields are not supposed to appear as results or to have an effect on
    * any predicated query being run.
    *
    * Defaults to an empty set.
    */
  protected def forkRelatedFields: Set[String] = Set.empty

  /** Generates extra predicates to skip whole records that would make the fork logic apparent in results.
    * A common implementation will add a filter on the "invalidation flag/date" field to be empty or false.
    *
    * Defaults to an empty list.
    */
  protected def hideForkResults(userQueryPredicates: List[Predicate]): List[Predicate] = List.empty

  /* select fields that would make the fork logic apparent as output to the queries */
  private def hiddenForkFields(f: Field): Boolean =
    forkRelatedFields.contains(f.field.toLowerCase)
  /* select fields that would make the fork logic apparent as aggregates in the queries */
  private def hiddenForkFields(a: Aggregation): Boolean =
    forkRelatedFields.contains(a.field.toLowerCase)
  /* select fields that would make the fork logic apparent as ordering in the queries */
  private def hiddenForkFields(o: QueryOrdering): Boolean =
    forkRelatedFields.contains(o.field.toLowerCase)
  /* hides from teh response the fields that would make the fork logic apparent */
  private def hideForkFields(r: QueryResponse): QueryResponse =
    r -- forkRelatedFields

}
