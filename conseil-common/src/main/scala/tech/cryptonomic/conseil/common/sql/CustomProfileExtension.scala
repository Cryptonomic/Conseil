package tech.cryptonomic.conseil.common.sql

import com.github.tminglei.slickpg.ExPostgresProfile
import slick.ast._
import slick.lifted.{Query, Rep}
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities
import com.github.tminglei.slickpg.agg.PgAggFuncSupport

/** Custom postgres profile for enabling `insertOrUpdateAll` */
trait CustomProfileExtension extends ExPostgresProfile with PgAggFuncSupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  /* The following values can be used via imports to add all aggregations supported
   * by pg-slick, which covers all postgres defined ones.
   * e.g.
   * {{{
   * import CustomProfileExtension.statisticsAggregations._
   * }}}
   *
   * There is a very strong limitation though, the current implementation
   * doesn't support aggregations combined with grouping of other columns.
   *
   * see the related issue here: https://github.com/tminglei/slick-pg/issues/289
   * To overcome this limitation, we introduced an additional extension of
   * slick-provided aggregations in the CustomApi object.
   * The latter is a totally different work-around and can't be used
   * with the pg-slick aggregations, as the two API designs are not compatible.
   */
  /** provides access to postgres general aggregations via pg-slick */
  lazy val generalAggregations = new GeneralAggFunctions {}

  /** provides access to postgres statistics aggregations via pg-slick */
  lazy val statisticsAggregations = new StatisticsAggFunctions {}

  /** provides access to postgres ordered-set aggregations via pg-slick */
  lazy val orderedSetAggregations = new OrderedSetAggFunctions {}

  /** provides access to postgres hypothetical-set aggregations via pg-slick */
  lazy val hypotheticalSetAggregations = new HypotheticalSetAggFunctions {}

  override val api: API = CustomApi

  /** Provide additional features from api import */
  object CustomApi extends API {

    /* The following conversions provides extra statistic operations from postgres
     * which are not generally available for any db-type
     * The use the same mechanisim used internally by slick, which provides aggregate functions
     * only for queries that results in single-column values.
     * The column can contain a value or an optional value (i.e. it works for nullable columns too)
     */
    implicit def customSingleColumnQueryExtensions[E: BaseTypedType, C[_]](q: Query[Rep[E], _, C]) =
      new CustomSingleColumnQueryExtensions[E, E, C](q)
    implicit def customSingleOptionColumnQueryExtensions[E: BaseTypedType, C[_]](q: Query[Rep[Option[E]], _, C]) =
      new CustomSingleColumnQueryExtensions[E, Option[E], C](q)
  }

}

/** Allow using the extensions under an import in scope */
object CustomProfileExtension extends CustomProfileExtension {
  import slick.ast.Library._

  /** Defines extra functions that we use for statistical computations */
  object Library {
    /* slick-compatible definitions, based on the internal implementation of slick-provided
     * extensions for sql functions
     */
    val StdDevAggregate = new SqlAggregateFunction("stddev")
    val StdDevPopAggregate = new SqlAggregateFunction("stddev_pop")
    val StdDevSampAggregate = new SqlAggregateFunction("stddev_samp")
  }
}

/** The implementation of extra functions added via implicit conversions of the query type */
final class CustomSingleColumnQueryExtensions[A, E, C[_]](val q: Query[Rep[E], _, C]) extends AnyVal {
  import slick.lifted.FunctionSymbolExtensionMethods._

  /** historical alias for [[stdDevSamp]] */
  def stdDev(implicit tt: TypedType[Option[A]]) =
    CustomProfileExtension.Library.StdDevAggregate.column[Option[A]](q.toNode)

  /** population standard deviation of the input values */
  def stdDevPop(implicit tt: TypedType[Option[A]]) =
    CustomProfileExtension.Library.StdDevPopAggregate.column[Option[A]](q.toNode)

  /** sample standard deviation of the input values */
  def stdDevSamp(implicit tt: TypedType[Option[A]]) =
    CustomProfileExtension.Library.StdDevSampAggregate.column[Option[A]](q.toNode)
}
