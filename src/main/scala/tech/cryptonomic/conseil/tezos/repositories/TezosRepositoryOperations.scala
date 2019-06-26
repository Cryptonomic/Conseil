package tech.cryptonomic.conseil.tezos.repositories

import tech.cryptonomic.conseil.tezos.Fees
import tech.cryptonomic.conseil.tezos.Fees.AverageFees
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  Block,
  BlockHash,
  BlockReference,
  BlockTagged,
  ContractId,
  Delegate,
  PublicKeyHash,
  Voting
}

/** Defines general query capabitilities dynamically built on
  * the abstract concept of storage `Table`s, `Column`s
  * and `Value`s for each record of a column
  * The effect of the result is generic as well.
  */
trait GenericQuerying[Eff[_], Table, Column, Value] {
  import tech.cryptonomic.conseil.generic.chain.DataTypes.{Aggregation, Predicate, QueryOrdering}
  import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType

  /** Counts number of rows in the given table
    * @param table  table
    * @return       amount of rows in the table
    */
  def countRows(table: Table): Eff[Int]

  /** Counts number of distinct elements by given table and column
    *
    * @param table  the table
    * @param column the column
    * @return       amount of distinct elements in given column
    */
  def countDistinct(table: Table, column: Column): Eff[Int]

  /** Selects distinct elements by given table and column
    *
    * @param table  the table
    * @param column the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(table: Table, column: Column): Eff[List[Value]]

  /**
    * Selects distinct elements by given table and column with filter
    *
    * @param table          the table
    * @param column         the column
    * @param matchingString string which is being matched
    * @return               distinct elements in given column as a list
    */
  def selectDistinctLike(table: Table, column: Column, matchingString: String): Eff[List[Value]]

  /** Selects elements filtered by the predicates
    *
    * @param table        the table
    * @param columns      list of columns
    * @param predicates   list of predicates for query to be filtered with
    * @param ordering     list of ordering conditions for the query
    * @param aggregation  optional aggregation
    * @param OutputType   the output representation of the results, enumerated as `OutputType`
    * @param limit        max number of rows fetched
    * @return             list of map of [column, any], which represents list of rows as a map of column to possible value
    */
  def selectWithPredicates(
      table: Table,
      columns: List[Column],
      predicates: List[Predicate],
      ordering: List[QueryOrdering],
      aggregation: List[Aggregation],
      outputType: OutputType,
      limit: Int
  ): Eff[List[Map[Column, Option[Any]]]]

}

/** Defines repository operations related to chain blocks
  * Parametric on the returned effect type wrapper
  */
trait BlocksRepository[Eff[_]] {

  /** is there any block stored at all? */
  def anyBlockAvailable: Eff[Boolean]

  /** Checks if a block for this hash is stored on db
    * @param hash Identifies the block
    * @return     true if block exists
    */
  def blockExists(hash: BlockHash): Eff[Boolean]

  /** Computes the max level of blocks.
    * @return the max level found or none
    */
  def fetchMaxBlockLevel: Eff[Option[Int]]

  /** Fetch information on a block related to it being part
    * of a fork in the chain.
    * Invalid meaning detected on a fork which has been rejected currently.
    * Semantic is
    * - None: block was never marked invalid
    * - Some(true): block is currently invalid
    * - Some(false): block was invalid but is not anymore
    *
    * This should be restored when the forking is handled by Lorre
    */
  //def blockIsInvalidated(hash: BlockHash): Eff[Option[Boolean]]

  /** Checks if a block for this hash and related operations are stored on db
    * @param hash Identifies the block
    * @return     true if block and operations exists
    */
  @deprecated("marked for removal, probably not needed", "since 0.1925.0009")
  def blockAndOpsExists(hash: BlockHash): Eff[Boolean]

  /**
    * Writes blocks data to a database, with no extra information.
    * @param blocks   Block
    * @return         the number of rows written
    */
  def writeBlocks(blocks: List[Block]): Eff[Option[Int]]

}

/** Defines repository calls related to chain operations.
  * Parametric on the returned effect type wrapper
  * as well as the represetnation for chain
  * operations, groups, stored operation ids, updates to balances.
  */
trait OperationsRepository[Eff[_], OperationGroup, Operation, OperationId, BalanceUpdates] {

  /** Reads in all operations referring to the group
    * @param groupHash is the group identifier
    * @return the operations and the collecting group, if there's one for the given hash, else `None`
    */
  def operationsForGroup(groupHash: String): Eff[Option[(OperationGroup, Seq[Operation])]]

  /** Writes a given group of operations, returning the records stored */
  def writeOperationsGroups(groups: List[OperationGroup]): Eff[Option[Int]]

  /** Writes a given operation, returning the newly corresponding generated id */
  def writeOperationWithNewId(operation: Operation): Eff[OperationId]

  /** Writes updates to accounts balances, returning the records stored
    * The balances can optionally refer to the operation that did the transaction
    */
  def writeUpdates(updates: List[BalanceUpdates], sourceOperation: Option[OperationId] = None): Eff[Option[Int]]

}

/** Defines repository operations related to chain voting data.
  * Parametric on the returned effect type wrapper
  */
trait VotingRepository[Eff[_]] {

  /** Writes several governance proposals, returning the records stored */
  def writeVotingProposals(proposals: List[Voting.Proposal]): Eff[Option[Int]]

  /** Writes several bakers rolls referencing the containing block, returning the records stored */
  def writeVotingRolls(bakers: List[Voting.BakerRolls], block: Block): Eff[Option[Int]]

  /** Writes several ballot results referencing the containing block, returning the records stored */
  def writeVotingBallots(ballots: List[Voting.Ballot], block: Block): Eff[Option[Int]]

}

/** Defines repository operations related to accounts stored in the chain.
  * Parametric on the returned effect type wrapper
  */
trait AccountsRepository[Eff[_]] {

  /** Reads the account ids in the checkpoint table, considering
    * only those at the latest block level (highest value)
    * @return the list of relevant ids and block references
    */
  def getLatestAccountsFromCheckpoint: Eff[Map[AccountId, BlockReference]]

  /** Removes data from the accounts checkpoint table
    * @param ids a selection of ids to remove. If none is specified the checkpoint is emptied
    */
  def cleanAccountsCheckpoint(ids: Option[Set[AccountId]] = None): Eff[Int]

  /** Writes accounts with block data to a database, updating any existing.
    * @param accountsInfo List data on the accounts and the corresponding blocks that operated on those
    * @return the number of rows written (if available from the underlying driver)
    */
  def updateAccounts(accountsInfo: List[BlockTagged[Map[AccountId, Account]]]): Eff[Int]

  /** Writes association of account ids and block data to define accounts that needs update
    * @param accountIds will have block information, paired with corresponding account ids to store
    * @return the rows written (if available form the underlying driver)
    */
  def writeAccountsCheckpoint(accountIds: List[(BlockHash, Int, List[AccountId])]): Eff[Option[Int]]

}

/** Defines repository operations related to chain delegates contracts.
  * Parametric on the returned effect type wrapper
  */
trait DelegatesRepository[Eff[_]] {

  /** Reads the delegate key hashes in the checkpoint table, considering
    * only those at the latest block level (highest value)
    * @return the list of relevant rows
    */
  def getLatestDelegatesFromCheckpoint: Eff[Map[PublicKeyHash, BlockReference]]

  /** Removes data from the ddelegates checkpoint table
    * @param pkhs a selection of key hashes to remove. If none is specified the checkpoint is emptied
    */
  def cleanDelegatesCheckpoint(pkhs: Option[Set[PublicKeyHash]] = None): Eff[Int]

  /** Writes delegates with block data to a database, updating any existing.
    * @param delegatesInfo List data on the delegates and the corresponding blocks that operated on those
    * @return the number of rows written (if available from the underlying driver)
    */
  def updateDelegates(delegatesInfo: List[BlockTagged[Map[PublicKeyHash, Delegate]]]): Eff[Int]

  /** Selects accounts corresponding to the given ids and copy the rows
    * as delegated contracts, whose data structure should match at least in part.
    */
  def copyAccountsAsDelegateContracts(contractsIds: Set[ContractId]): Eff[Option[Int]]

  /** Writes association of delegate key-hashes and block data to define delegates that needs to be written
    * @param delegatesKeyHashes will have block information, paired with corresponding hashes to store
    * @return the rows written (if available form the underlying driver)
    */
  def writeDelegatesCheckpoint(delegatesKeyHashes: List[(BlockHash, Int, List[PublicKeyHash])]): Eff[Option[Int]]

}

/** Defines repository operations related to computation of global transaction fees.
  * Parametric on the returned effect type wrapper
  */
trait FeesRepository[Eff[_]] {

  /** Given the operation kind, return range of fees and timestamp for that operation.
    * @param kind Operation kind
    * @param numberOfFeesAveraged How many values to use for statistics computations
    * @return The average fees for a given operation kind, if it exists
    */
  def calculateAverageFees(kind: Fees.OperationKind, numberOfFeesAveraged: Int): Eff[Option[AverageFees]]

  /** Writes computed fees averages to a database.
    * @param fees List of average fees for different operation kinds
    * @return the number of rows written (if available from the underlying driver)
    */
  def writeFees(fees: List[AverageFees]): Eff[Option[Int]]

}
