package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.Tables.{FeesRow, BlocksRow}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}

/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  private val conf = ConfigFactory.load
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
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @param ec ExecutionContext needed to invoke the data fetching using async results
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
    * @param ec ExecutionContext needed to invoke the data fetching using async results
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
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return List of operation groups
    */
  def fetchOperationGroups(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.OperationGroupsRow], ec: ExecutionContext): Future[Seq[Tables.OperationGroupsRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

  /**
    * Fetches all operations.
    * @param filter Filters to apply
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @return List of operations
    */
  def fetchOperations(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.OperationsRow]): Future[Seq[Tables.OperationsRow]] =
    apiFilters(filter)(0)

  /**
    * Given the operation kind and the number of columns wanted,
    *
    * return the mean (along with +/- one standard deviation) of
    * fees incurred in those operations.
    * @param filter Filters to apply, specifically operation kinds
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return AverageFee class, getting low, medium, and high
    *           estimates for average fees, timestamp the calculation
    *           was performed at, and the kind of operation being
    *           averaged over.
    */
  def fetchAverageFees(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.FeesRow], ec: ExecutionContext): Future[AverageFees] =
    apiFilters(filter)(0)
      .map( rows =>
        rows.head match {
          case FeesRow(low, medium, high, timestamp, kind) => AverageFees(low, medium, high, timestamp, kind)
        }
      )

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
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return The account with its associated operation groups
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
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return List of accounts
    */
  def fetchAccounts(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.AccountsRow], ec: ExecutionContext): Future[Seq[Tables.AccountsRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

  /**
    * @param ec ExecutionContext needed to invoke the data fetching using async results
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

