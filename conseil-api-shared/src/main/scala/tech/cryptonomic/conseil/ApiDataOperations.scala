package tech.cryptonomic.conseil

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.generic.chain.DataOperations
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
// TODO: weird import
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.sql.DatabaseRunner
import tech.cryptonomic.conseil.common.util.DatabaseUtil.QueryBuilder._
import tech.cryptonomic.conseil.platform.Sanitizer._

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
trait ApiDataOperations extends DatabaseRunner with DataOperations with ConseilLogSupport {
  import ApiDataOperations._

  /** Executes the query with given predicates
    *
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a map
    * */
  override def queryWithPredicates(prefix: String, tableName: String, query: Query, hideForkInvalid: Boolean = false)(
      implicit ec: ExecutionContext
  ): Future[List[QueryResponse]] = {
    val (fields, predicates, aggregation, ordering) = (
      sanitizeFields(query.fields),
      sanitizePredicates(query.predicates),
      query.aggregation,
      query.orderBy
    )
    val validFields = if (hideForkInvalid) fields.filterNot(hiddenForkFields) else fields
    val validPredicates = predicates ++ (if (hideForkInvalid) hideForkResults(predicates) else Nil)
    val validAggregation = if (hideForkInvalid) aggregation.filterNot(hiddenForkFields) else aggregation
    val validOrdering = if (hideForkInvalid) ordering.filterNot(hiddenForkFields) else ordering
    runQuery(
      selectWithPredicates(
        prefix,
        tableName,
        validFields,
        validPredicates,
        validAggregation,
        validOrdering,
        query.temporalPartition,
        query.snapshot,
        query.output,
        query.limit
      )
    )
  }

  /**
    * Selects elements filtered by the predicates
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param columns        list of column names
    * @param predicates     list of predicates for query to be filtered with
    * @param aggregation    optional aggregation
    * @param ordering       list of ordering conditions for the query
    * @param limit          max number of rows fetched
    * @return               list of map of [string, any], which represents list of rows as a map of column name to value
    */
  def selectWithPredicates(
      prefix: String,
      table: String,
      columns: List[Field],
      predicates: List[Predicate],
      aggregation: List[Aggregation],
      ordering: List[QueryOrdering],
      temporalPartition: Option[String],
      snapshot: Option[Snapshot],
      outputType: OutputType,
      limit: Int
  )(implicit ec: ExecutionContext): DBIO[List[QueryResponse]] = {
    /* we add [pre/post]-processing of the queries to take into
     * account the need to hide any fork-invalidated data
     */
    val tableWithPrefix = prefix + "." + table
    val q = (temporalPartition, snapshot) match {
      case (Some(tempPartition), Some(snap)) =>
        makeTemporalQuery(
          tableWithPrefix,
          columns,
          predicates,
          aggregation,
          ordering,
          tempPartition,
          snap,
          limit
        )
      case _ =>
        makeQuery(tableWithPrefix, columns, aggregation)
          .addPredicates(predicates)
          .addGroupBy(aggregation, columns)
          .addHaving(aggregation)
          .addOrdering(ordering)
          .addLimit(limit)
    }

    logger.debug(s"Received an entity query as follows ${q.queryParts.mkString("")}")

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
  protected val hideForkResults = (_: List[Predicate]) => List.empty[Predicate]

  /* select fields that would make the fork logic apparent as output to the queries */
  private def hiddenForkFields(f: Field): Boolean =
    forkRelatedFields.contains(f.field.toLowerCase)
  /* select fields that would make the fork logic apparent as aggregates in the queries */
  private def hiddenForkFields(a: Aggregation): Boolean =
    forkRelatedFields.contains(a.field.toLowerCase)
  /* select fields that would make the fork logic apparent as ordering in the queries */
  private def hiddenForkFields(o: QueryOrdering): Boolean =
    forkRelatedFields.contains(o.field.toLowerCase)
  /* hides from the response the fields that would make the fork logic apparent */
  private def hideForkFields(r: QueryResponse): QueryResponse =
    r -- forkRelatedFields

}
