package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.Tables.{FeesRow, BlocksRow}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  private val conf = ConfigFactory.load
  val awaitTimeInSeconds: Int = conf.getInt("dbAwaitTimeInSeconds")
  lazy val dbHandle: Database = DatabaseUtil.db

  import Filter._

  /**
    * Repesents a query filter submitted to the Conseil API.
    *
    * @param limit                  How many records to return
    * @param blockIDs               Block IDs
    * @param levels                 Block levels
    * @param chainIDs               Chain IDs
    * @param protocols              Protocols
    * @param operationGroupIDs      Operation IDs
    * @param operationSources       Operation sources
    * @param operationDestinations  Operation destinations
    * @param operationParticipants  Operations sources or destinations
    * @param accountIDs             Account IDs
    * @param accountManagers        Account managers
    * @param accountDelegates       Account delegates
    * @param operationKinds         Operation outer kind
    * @param sortBy                 Database column name to sort by
    * @param order                  Sort items ascending or descending
    */
  case class Filter(
                     limit: Option[Int] = Some(defaultLimit),
                     blockIDs: Option[Set[String]] = emptyOptions,
                     levels: Option[Set[Int]] = emptyOptions,
                     chainIDs: Option[Set[String]] = emptyOptions,
                     protocols: Option[Set[String]] = emptyOptions,
                     operationGroupIDs: Option[Set[String]] = emptyOptions,
                     operationSources: Option[Set[String]] = emptyOptions,
                     operationDestinations: Option[Set[String]] = emptyOptions,
                     operationParticipants: Option[Set[String]] = emptyOptions,
                     operationKinds: Option[Set[String]] = emptyOptions,
                     accountIDs: Option[Set[String]] = emptyOptions,
                     accountManagers: Option[Set[String]] = emptyOptions,
                     accountDelegates: Option[Set[String]] = emptyOptions,
                     sortBy: Option[String] = None,
                     order: Option[String] = Some("DESC")
                   )

  object Filter {

    // Common values

    // default limit on output results, if not available as call input
    val defaultLimit = 10

    private def emptyOptions[A] = Some(Set.empty[A])

  }

  /**
    * Represents queries for filtered tables for Accounts, Blocks, Operation Groups, and Operations.
    *
    * @param filteredAccounts         Filtered Accounts table
    * @param filteredBlocks           Filtered Blocks table
    * @param filteredOperationGroups  Filtered OperationGroups table
    * @param filteredOperations       Filtered Operations table
   */
  case class FilteredTables(
                             filteredAccounts: Query[Tables.Accounts, Tables.AccountsRow, Seq],
                             filteredBlocks: Query[Tables.Blocks, Tables.BlocksRow, Seq],
                             filteredOperationGroups: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq],
                             filteredOperations: Query[Tables.Operations, Tables.OperationsRow, Seq]
                           )

  /**
    * Represents all possible joins of tables that can be made from Accounts, Blocks, Operation Groups, and Operations.
    *
    * Example: BlocksOperationGroupsOperationsAccounts corresponds to the four way inner join between the Blocks,
    * Operation Groups, Operations, and Accounts tables.
    *
    * Example: OperationGroupsOperationsAccounts corresponds to the three way join between the Operation Groups,
    * Operations, and Accounts Tables.
    */

  sealed trait JoinedTables

  case class BlocksOperationGroupsOperationsAccounts(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups, Tables.Operations, Tables.Accounts),
      (Tables.BlocksRow, Tables.OperationGroupsRow, Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationGroupsOperations(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups, Tables.Operations),
      (Tables.BlocksRow, Tables.OperationGroupsRow, Tables.OperationsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationGroupsAccounts(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups, Tables.Accounts),
      (Tables.BlocksRow, Tables.OperationGroupsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationGroups(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups),
      (Tables.BlocksRow, Tables.OperationGroupsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationsAccounts(
    join: Query[
      (Tables.Blocks, Tables.Operations, Tables.Accounts),
      (Tables.BlocksRow, Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperations(
    join: Query[
      (Tables.Blocks, Tables.Operations),
      (Tables.BlocksRow, Tables.OperationsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksAccounts(
    join: Query[
      (Tables.Blocks, Tables.Accounts),
      (Tables.BlocksRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class Blocks(
    join: Query[Tables.Blocks, Tables.BlocksRow, Seq]
  ) extends JoinedTables

  case class OperationGroupsOperationsAccounts(
    join: Query[
      (Tables.OperationGroups, Tables.Operations, Tables.Accounts),
      (Tables.OperationGroupsRow, Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class OperationGroupsOperations(
    join: Query[
      (Tables.OperationGroups, Tables.Operations),
      (Tables.OperationGroupsRow, Tables.OperationsRow),
      Seq]
  ) extends JoinedTables

  case class OperationGroupsAccounts(
    join: Query[
      (Tables.OperationGroups, Tables.Accounts),
      (Tables.OperationGroupsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class OperationGroups(
    join: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq]
  ) extends JoinedTables

  case class OperationsAccounts(
    join: Query[
      (Tables.Operations, Tables.Accounts),
      (Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class Operations(
    join: Query[Tables.Operations, Tables.OperationsRow, Seq]
  ) extends JoinedTables

  case class Accounts(
    join: Query[Tables.Accounts, Tables.AccountsRow, Seq]
  ) extends JoinedTables

  case object EmptyJoin extends JoinedTables

  /**
    * This represents a database query that returns all of the columns of the table in a scala tuple.
    * The only options available are for the Blocks, Operation Groups, and Operations Table,
    * corresponding to the functions fetchBlocks, fetchOperationGroups, and fetchOperations, and these
    * types are used for convenience in fetchSortedTables.
    */
  sealed trait Action

  case class BlocksAction(action: Query[Tables.Blocks, Tables.BlocksRow, Seq]) extends Action

  case class OperationGroupsAction(action: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq]) extends Action

  case class AccountsAction(action: Query[Tables.Accounts, Tables.AccountsRow, Seq]) extends Action

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = dbHandle.run(Tables.Blocks.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the most recent block stored in the database.
    *
    * @return Latest block.
    */
  def fetchLatestBlock()(implicit ec: ExecutionContext): Future[Option[Tables.BlocksRow]] =
    dbHandle.run(latestBlockIO())

  /**
    * Fetches a block by block hash from the db.
    *
    * @param hash The block's hash
    * @return The block along with its operations
    */
  @throws[NoSuchElementException]("when the hash doesn't match any block")
  def fetchBlock(hash: String)(implicit ec: ExecutionContext): Future[Map[String, Any]] = {
    val joins = for {
      groups <- Tables.OperationGroups if groups.blockId === hash
      block <- groups.blocksFk
    } yield (block, groups)

    dbHandle.run(joins.result).map { paired =>
      val (blocks, groups) = paired.unzip
      Map("block" -> blocks.head, "operation_groups" -> groups)
    }
  }

  /**
    * Fetches all blocks from the db.
    *
    * @param filter Filters to apply
    * @return List of blocks
    */
  def fetchBlocks(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.BlocksRow], ec: ExecutionContext): Future[Seq[Tables.BlocksRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

  /**
    * Fetch a given operation group
    *
    * Running the returned operation will fail with [[NoSuchElementException]] if
    *  - no block is found on the db
    *  - no group corresponds to the given hash
    *
    * @param operationGroupHash Operation group hash
    * @return Operation group along with associated operations and accounts
    */
  def fetchOperationGroup(operationGroupHash: String)(implicit ec: ExecutionContext): Future[Map[String, Any]] = {
    val groupedOpsIO = latestBlockIO().collect { // we fail the operation if no block is there
      case Some(_) =>
        TezosDatabaseOperations.operationsForGroupIO(operationGroupHash).map(_.get) // we want to fail here too
    }.flatten

    //convert to a valid object for the caller
    dbHandle.run(groupedOpsIO).map {
      case (opGroup, operations) =>
        Map(
          "operation_group" -> opGroup,
          "operations" -> operations
        )
    }
  }

  /**
    * Fetches all operation groups.
    * @param filter Filters to apply
    * @return List of operation groups
    */
  def fetchOperationGroups(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.OperationGroupsRow], ec: ExecutionContext): Future[Seq[Tables.OperationGroupsRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

  /**
    * Fetches all operations.
    * @param filter Filters to apply
    * @return List of operations
    */
  def fetchOperations(filter: Filter): Future[Seq[Tables.OperationsRow]] = {
    val action = for {
      o <- Tables.Operations
      og <- o.operationGroupsFk
      b <- o.blocksFk
    } yield (o, b)

    import ApiFiltering.Queries._

    val filtered = action.filter { case (o, b) =>
      filterOperationIDs(filter, o) &&
      filterOperationSources(filter, o) &&
      filterOperationDestinations(filter, o) &&
      filterOperationParticipants(filter, o) &&
      filterOperationKinds(filter, o) &&
      filterBlockIDs(filter, b) &&
      filterBlockLevels(filter, b) &&
      filterChainIDs(filter, b) &&
      filterProtocols(filter, b)
    }.map(_._1)

    dbHandle.run(
      filtered.distinct
        .sortBy(_.blockLevel.desc)
        .take(ApiFiltering.getFilterLimit(filter))
        .result
    )
  }

  /**
    * Given the operation kind and the number of columns wanted,
    *
    * return the mean (along with +/- one standard deviation) of
    * fees incurred in those operations.
    * @param filter Filters to apply, specifically operation kinds
    * @return AverageFee class, getting low, medium, and high
    *         estimates for average fees, timestamp the calculation
    *         was performed at, and the kind of operation being
    *         averaged over.
    */
  def fetchAverageFees(filter: Filter): Try[AverageFees] = Try {
    val action =
      Tables.Fees
        .filter (
          fee => ApiFiltering.Queries.filterOperationKindsForFees(filter, fee)
        )
        .distinct
        .sortBy(_.timestamp.desc)
        .take(1)
        .result
        .head

    val row = Await.result(dbHandle.run(action), awaitTimeInSeconds.seconds)
    row match {
      case FeesRow(low, medium, high, timestamp, kind) =>
       AverageFees(low, medium, high, timestamp, kind)
    }

  }

  /**
    * Fetches the level of the most recent block in the accounts table.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxBlockLevelForAccounts(): Future[BigDecimal] =
    dbHandle.run(TezosDb.fetchAccountsMaxBlockLevel)

  /**
    * Fetches an account by account id from the db.
    * @param account_id The account's id number
    * @return           The account with its associated operation groups
    */
  def fetchAccount(account_id: String)(implicit ec: ExecutionContext): Future[Map[String, Any]] = {
    val fetchOperation = TezosDb.fetchAccountsMaxBlockLevel.flatMap {
      latestBlockLevel =>
        Tables.Accounts
          .filter(row =>
            row.blockLevel === latestBlockLevel && row.accountId === account_id
          ).take(1)
          .result
    }
    dbHandle.run(fetchOperation).map{
      account =>
        Map("account" -> account)
    }
  }

  /**
    * Fetches a list of accounts from the db.
    * @param filter Filters to apply
    * @return       List of accounts
    */
  def fetchAccounts(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.AccountsRow], ec: ExecutionContext): Future[Seq[Tables.AccountsRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

  /**
    * @return the most recent block, if one exists in the database.
    */
  private[tezos] def latestBlockIO()(implicit ec: ExecutionContext): DBIO[Option[BlocksRow]] =
    TezosDb.fetchMaxBlockLevel.flatMap(
      maxLevel =>
        Tables.Blocks
          .filter(_.level === maxLevel)
          .take(1)
          .result
          .headOption
    )

}

