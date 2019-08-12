package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.generic.chain.DataTypes
import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder
import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

/** Provides the query building blocks for dynamic query
  * generation to access tezos entities in tables
  */
object DatabaseQueries extends LazyLogging {

  /** A typed wrapper to avoid using strings for implicit summoning of am
    * appropriate QueryBuilder for a tezos table
    */
  case class TableRef(name: String) extends AnyVal

  /* builds the simplest possible query */
  private def simpleQueryBuilder[T <: Table[_]]: QueryBuilder[T] =
    (table, columns, aggregation) => {
      val qualify = fullyQualifyColumn(table)
      val aggregationFields =
        aggregation.map { aggr =>
          mapAggregationToSQL(qualify)(aggr.function, aggr.field) + " as " + mapAggregationToAlias(
            aggr.function,
            aggr.field
          )
        }
      val projection = aggregationFields ::: columns.toSet.diff(aggregation.map(_.field).toSet).map(qualify).toList
      val cols = if (projection.isEmpty) qualify("*") else projection.mkString(", ")
      sql"""SELECT #$cols FROM #$table WHERE true """
    }

  /* Builds a query that removes entities referring to invalidated blocks, based on
   * @param tableJoiner given the invalidation table name and the block hash column name, defines
   *        the clause which follows the FROM
   *
   * In general, it should allow to build queries in this form:
   * SELECT cols
   * FROM table LEFT OUTER JOIN InvalidatedBlocks inv ON table.block_id = inv.hash
   * WHERE COALESCE(inv.is_invalidated, false) is false
   */
  private def blockValidityCheckerBuilder[T <: Table[_]](
      tableJoiner: (String, String) => String
  ) = new QueryBuilder[T] {
    override def makeQuery(
        table: String,
        columns: List[String],
        aggregation: List[DataTypes.Aggregation]
    ) = {
      val qualify = fullyQualifyColumn(table)
      val aggregationFields =
        aggregation.map { aggr =>
          mapAggregationToSQL(qualify)(aggr.function, aggr.field) + " as " + mapAggregationToAlias(
            aggr.function,
            aggr.field
          )
        }
      val projection = aggregationFields ::: columns.toSet.diff(aggregation.map(_.field).toSet).map(qualify).toList
      val cols = if (projection.isEmpty) qualify("*") else projection.mkString(", ")
      val invalidationFrom = tableJoiner(invalidatedTableName, blockFKColumnName)
      sql"""SELECT #$cols FROM #$table #$invalidationFrom
          | WHERE COALESCE(#$invalidatedColumnName, false) is false """.stripMargin
    }
  }

  /* Custom query to select balance updates that refer to valid blocks
   * It's complicated by the fact that the row can refer to blocks directly
   * or via opeations, using two distinct and nullable columns as external keys reference
   */
  private object balanceUpdatesBuilder extends QueryBuilder[Tables.BalanceUpdates] {
    override def makeQuery(
        table: String,
        columns: List[String],
        aggregation: List[DataTypes.Aggregation]
    ) = {
      val qualify = fullyQualifyColumn(table)
      val aggregationFields =
        aggregation.map { aggr =>
          mapAggregationToSQL(qualify)(aggr.function, aggr.field) + " as " + mapAggregationToAlias(
            aggr.function,
            aggr.field
          )
        }
      val projection = aggregationFields ::: columns.toSet.diff(aggregation.map(_.field).toSet).map(qualify).toList
      val cols = if (projection.isEmpty) qualify("*") else projection.mkString(", ")

      val balances = Tables.BalanceUpdates.baseTableRow
      val operations = Tables.Operations.baseTableRow
      val sourceCol = balances.source.column.toString()
      val nestedOperationIdColumn = operations.operationId.column.toString().split('.').last

      val nestedQuery =
        s"""SELECT ${operations.operationId.column} FROM ${operations.tableName}
         | LEFT OUTER JOIN $invalidatedTableName ON ${operations.blockHash.column} = $blockFKColumnName
         | WHERE COALESCE($invalidatedColumnName, false) is false""".stripMargin

      val blockJoin =
        s"LEFT OUTER JOIN $invalidatedTableName ON ${balances.sourceHash.column} = $blockFKColumnName"
      val nestedResultJoin =
        s"LEFT OUTER JOIN valid_operation ON ${balances.sourceId.column} = valid_operation.$nestedOperationIdColumn"

      sql"""WITH valid_operation AS (
          |   #$nestedQuery
          | )
          | SELECT #$cols FROM #$table #$blockJoin #$nestedResultJoin
          | WHERE (
          |   #$sourceCol = 'block' AND COALESCE(#$invalidatedColumnName, false) is false
          | ) OR (
          |   #$sourceCol IN ('operation', 'operation_result') AND valid_operation.#$nestedOperationIdColumn is not null
          | ) """.stripMargin
    }
  }

  /* Provides builders at runtime, each based on an existing table definition.
   * Having the type contraint on the Table Type helps out keeping everything
   * updated when changing or adding to the database schema.
   * It's checked at runtime only, but unit testing should help reducing the risk
   * of introducing regressions.
   */
  implicit def builderFor(table: TableRef): QueryBuilder[Table[_]] = builderDefs(table.name)

  private[this] val builderDefs: String => QueryBuilder[Table[_]] = {
    val backingMap = Map(
      Tables.Accounts.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Accounts](
            tableJoiner = (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Accounts.baseTableRow.blockId.column} = $fk"
          ),
      Tables.BalanceUpdates.baseTableRow.tableName -> balanceUpdatesBuilder,
      Tables.Ballots.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Ballots](
            tableJoiner = (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Ballots.baseTableRow.blockId.column} = $fk"
          ),
      Tables.Blocks.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Blocks](
            tableJoiner = (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Blocks.baseTableRow.hash.column} = $fk"
          ),
      Tables.DelegatedContracts.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.DelegatedContracts](
            tableJoiner = (inv, fk) => {
              val delegates = Tables.Delegates.baseTableRow
              val delegated = Tables.DelegatedContracts.baseTableRow
              s"""JOIN ${delegates.tableName} ON ${delegated.delegateValue.column} = ${delegates.pkh.column}
                | LEFT OUTER JOIN $inv ON ${delegates.blockId.column} = $fk""".stripMargin
            }
          ),
      Tables.Delegates.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Delegates](
            tableJoiner = (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Delegates.baseTableRow.blockId.column} = $fk"
          ),
      Tables.Fees.baseTableRow.tableName -> simpleQueryBuilder[Tables.Fees],
      Tables.OperationGroups.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.OperationGroups](
            tableJoiner =
              (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.OperationGroups.baseTableRow.blockId.column} = $fk"
          ),
      Tables.Operations.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Operations](
            tableJoiner =
              (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Operations.baseTableRow.blockHash.column} = $fk"
          ),
      Tables.Proposals.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Proposals](
            tableJoiner = (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Proposals.baseTableRow.blockId.column} = $fk"
          ),
      Tables.Rolls.baseTableRow.tableName ->
          blockValidityCheckerBuilder[Tables.Rolls](
            tableJoiner = (inv, fk) => s"LEFT OUTER JOIN $inv ON ${Tables.Rolls.baseTableRow.blockId.column} = $fk"
          )
    )
    logger.info(
      """
        |The following tezos tables have generic builders for their queries defined:
        |{}""".stripMargin,
      backingMap.keySet.mkString("- ", "\n- ", "\n")
    )
    backingMap
  }

}
