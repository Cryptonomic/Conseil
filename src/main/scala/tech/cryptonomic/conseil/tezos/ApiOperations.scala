package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.generic.chain.{DataOperations, DataTypes}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.generic.chain.DataTypes.{OperationType, Predicate, Query}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations.{areFieldsValid, sanitizeForSql}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}

/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations extends DataOperations {

  lazy val dbHandle: Database = DatabaseUtil.db

  /** Define sorting order for api queries */
  sealed trait Sorting extends Product with Serializable
  case object AscendingSort extends Sorting
  case object DescendingSort extends Sorting
  object Sorting {

    /** Read an input string (`asc` or `desc`) to return a
      * (possible) [[tech.cryptonomic.conseil.tezos.ApiOperations.Sorting]] value
      */
    def fromString(s: String): Option[Sorting] = s.toLowerCase match {
      case "asc" => Some(AscendingSort)
      case "desc" => Some(DescendingSort)
      case _ => None
    }
  }

  import Filter._

  /**
    * Represents a query filter submitted to the Conseil API.
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
  final case class Filter(
                     limit: Option[Int] = Some(defaultLimit),
                     blockIDs: Set[String] = Set.empty,
                     levels: Set[Int] = Set.empty,
                     chainIDs: Set[String] = Set.empty,
                     protocols: Set[String] = Set.empty,
                     operationGroupIDs: Set[String] = Set.empty,
                     operationSources: Set[String] = Set.empty,
                     operationDestinations: Set[String] = Set.empty,
                     operationParticipants: Set[String] = Set.empty,
                     operationKinds: Set[String] = Set.empty,
                     accountIDs: Set[String] = Set.empty,
                     accountManagers: Set[String] = Set.empty,
                     accountDelegates: Set[String] = Set.empty,
                     sortBy: Option[String] = None,
                     order: Option[Sorting] = Some(DescendingSort)
                   ) {

    /** transforms Filter into a Query with a set of predicates */
    def toQuery: Query = {
      Query(
        fields = List.empty,
        predicates = List(
          Predicate(
            field = "block_id",
            operation = OperationType.in,
            set = blockIDs.toList
          ),
          Predicate(
            field = "level",
            operation = OperationType.in,
            set = levels.toList
          ),
          Predicate(
            field = "chain_id",
            operation = OperationType.in,
            set = chainIDs.toList
          ),
          Predicate(
            field = "protocol",
            operation = OperationType.in,
            set = protocols.toList
          ),
          Predicate(
            field = "level",
            operation = OperationType.in,
            set = levels.toList
          ),
          Predicate(
            field = "group_id",
            operation = OperationType.in,
            set = operationGroupIDs.toList
          ),
          Predicate(
            field = "source",
            operation = OperationType.in,
            set = operationSources.toList
          ),
          Predicate(
            field = "destination",
            operation = OperationType.in,
            set = operationDestinations.toList
          ),
          Predicate(
            field = "participant",
            operation = OperationType.in,
            set = operationParticipants.toList
          ),
          Predicate(
            field = "kind",
            operation = OperationType.in,
            set = operationKinds.toList
          ),
          Predicate(
            field = "account_id",
            operation = OperationType.in,
            set = accountIDs.toList
          ),
          Predicate(
            field = "manager",
            operation = OperationType.in,
            set = accountManagers.toList
          ),
          Predicate(
            field = "delegate",
            operation = OperationType.in,
            set = accountDelegates.toList
          )
        ).filter(_.set.nonEmpty),
        limit = limit.getOrElse(DataTypes.defaultLimitValue)
      )
    }
  }

  object Filter {

    /** builds a filter from incoming string-based parameters */
    def readParams(
      limit: Option[Int],
      blockIDs: Iterable[String],
      levels: Iterable[Int],
      chainIDs: Iterable[String],
      protocols: Iterable[String],
      operationGroupIDs: Iterable[String],
      operationSources: Iterable[String],
      operationDestinations: Iterable[String],
      operationParticipants: Iterable[String],
      operationKinds: Iterable[String],
      accountIDs: Iterable[String],
      accountManagers: Iterable[String],
      accountDelegates: Iterable[String],
      sortBy: Option[String],
      order: Option[String]
    ): Filter =
      Filter(
        limit,
        blockIDs.toSet,
        levels.toSet,
        chainIDs.toSet,
        protocols.toSet,
        operationGroupIDs.toSet,
        operationSources.toSet,
        operationDestinations.toSet,
        operationParticipants.toSet,
        operationKinds.toSet,
        accountIDs.toSet,
        accountManagers.toSet,
        accountDelegates.toSet,
        sortBy,
        order.flatMap(Sorting.fromString)
      )

    // Common values

    // default limit on output results, if not available as call input
    val defaultLimit = 10

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
    * @return The block along with its operations, if the hash matches anything
    */
  def fetchBlock(hash: BlockHash)(implicit ec: ExecutionContext): Future[Option[Map[Symbol, Any]]] = {
    val joins = for {
      groups <- Tables.OperationGroups if groups.blockId === hash.value
      block <- groups.blocksFk
    } yield (block, groups)

    dbHandle.run(joins.result).map { paired =>
      val (blocks, groups) = paired.unzip
      blocks.headOption.map {
        block => Map(
          'block -> block,
          'operation_groups -> groups
        )
      }
    }
  }

  /**
    * Fetches all blocks from the db.
    *
    * @param filter Filters to apply
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @return List of blocks
    */
  def fetchBlocks(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.BlocksRow]): Future[Seq[Tables.BlocksRow]] =
    apiFilters(filter)

  /**
    * Fetch a given operation group
    *
    * Running the returned operation will fail with `NoSuchElementException` if no block is found on the db
    *
    * @param operationGroupHash Operation group hash
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return Operation group along with associated operations and accounts
    */
  def fetchOperationGroup(operationGroupHash: String)(implicit ec: ExecutionContext): Future[Option[Map[Symbol, Any]]] = {
    val groupsMapIO = for {
      latest <- latestBlockIO if latest.nonEmpty
      operations <- TezosDatabaseOperations.operationsForGroup(operationGroupHash)
    } yield operations.map {
        case (opGroup, ops) =>
          Map(
            'operation_group -> opGroup,
            'operations -> ops
          )
        }

    dbHandle.run(groupsMapIO)
  }

  /**
    * Fetches all operation groups.
    * @param filter Filters to apply
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @return List of operation groups
    */
  def fetchOperationGroups(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.OperationGroupsRow]): Future[Seq[Tables.OperationGroupsRow]] =
    apiFilters(filter)

  /**
    * Fetches all operations.
    * @param filter Filters to apply
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @return List of operations
    */
  def fetchOperations(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.OperationsRow]): Future[Seq[Tables.OperationsRow]] =
    apiFilters(filter)

  /**
    * Given the operation kind return the mean (along with +/- one standard deviation)
    * of fees incurred in those operations.
    * @param filter Filters to apply, specifically operation kinds
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return AverageFee class, getting low, medium, and high
    *           estimates for average fees, timestamp the calculation
    *           was performed at, and the kind of operation being
    *           averaged over.
    */
  def fetchAverageFees(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.FeesRow], ec: ExecutionContext): Future[Option[AverageFees]] =
    apiFilters(filter)
      .map( rows =>
        rows.headOption map {
          case Tables.FeesRow(low, medium, high, timestamp, kind) => AverageFees(low, medium, high, timestamp, kind)
        }
      )

  /**
    * Fetches an account by account id from the db.
    * @param account_id The account's id number
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return The account with its associated operation groups
    */
  def fetchAccount(account_id: AccountId)(implicit ec: ExecutionContext): Future[Option[Map[String, Any]]] = {
    val fetchOperation =
        Tables.Accounts
          .filter(row => row.accountId === account_id.id)
          .take(1)
          .result

    dbHandle.run(fetchOperation).map{
      accounts =>
        accounts.headOption.map { account =>
          Map("account" -> account)
        }
    }
  }

  /**
    * Fetches a list of accounts from the db.
    * @param filter Filters to apply
    * @param apiFilters an instance in scope that actually executes filtered data-fetching
    * @return List of accounts
    */
  def fetchAccounts(filter: Filter)(implicit apiFilters: ApiFiltering[Future, Tables.AccountsRow]): Future[Seq[Tables.AccountsRow]] =
    apiFilters(filter)

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

  /**
    * Counts all entities in the db
    * @param ec ExecutionContext needed to invoke the data fetching using async results
    * @return Count with all amounts
    */
  def countAll(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    dbHandle.run {
      for {
        blocks <- TezosDb.countRows(Tables.Blocks)
        accounts <- TezosDb.countRows(Tables.Accounts)
        operationGroups <- TezosDb.countRows(Tables.OperationGroups)
        operations <- TezosDb.countRows(Tables.Operations)
        fees <- TezosDb.countRows(Tables.Fees)
      } yield
        Map(
          Tables.Blocks.baseTableRow.tableName -> blocks,
          Tables.Accounts.baseTableRow.tableName -> accounts,
          Tables.OperationGroups.baseTableRow.tableName -> operationGroups,
          Tables.Operations.baseTableRow.tableName -> operations,
          Tables.Fees.baseTableRow.tableName -> fees
        )
    }
  }

  /**
    * Runs DBIO action
    * @param  action action to be performed on db
    * @return result of DBIO action as a Future
    */
  def runQuery[A](action: DBIO[A]): Future[A] = {
    dbHandle.run {
      action
    }
  }

  /** Executes the query with given predicates
    *
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a map
    * */
  override def queryWithPredicates(tableName: String, query: Query)(implicit ec: ExecutionContext): Future[List[Map[String, Any]]] = {
    if (areFieldsValid(tableName, (query.fields ++ query.predicates.map(_.field) ++ query.orderBy.map(_.field)).toSet)) {
      runQuery(
        TezosDatabaseOperations.selectWithPredicates(
          tableName,
          query.fields,
          sanitizePredicates(query.predicates),
          query.orderBy,
          Math.min(query.limit, DataTypes.maxLimitValue)
        )
      )
    } else {
      Future.successful(List.empty)
    }
  }

  /** Sanitizes predicate values so query is safe from SQL injection */
  def sanitizePredicates(predicates: List[Predicate]): List[Predicate] = {
    predicates.map { predicate =>
      predicate.copy(set = predicate.set.map(field => sanitizeForSql(field.toString)))
    }
  }
}

