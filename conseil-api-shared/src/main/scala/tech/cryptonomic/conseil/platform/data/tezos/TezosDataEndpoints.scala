package tech.cryptonomic.conseil.platform.data.tezos

import sttp.tapir._

import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.tezos.Tables._

trait TezosDataEndpoints extends ApiDataEndpoints { // with TezosFilterFromQueryString {

  import tech.cryptonomic.conseil.platform.data.converters._
  import tech.cryptonomic.conseil.platform.data.schemas._

  implicit private val platform = "tezos"
  private val root = commonPath(platform)

  def tezosQueryEndpoint = queryEndpoint(platform)

  /** V2 Blocks endpoint definition */
  def tezosBlocksEndpoint =
    root.get
      .in("blocks" / TezosFilterFromQueryString.tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("blocks"))

  /** V2 Blocks head endpoint definition */
  def tezosBlocksHeadEndpoint =
    root.get
      .in("blocks" / "head")
      .out(compatibilityQuery[BlocksRow]("blocks head"))

  /** V2 Blocks by hash endpoint definition */
  def tezosBlockByHashEndpoint =
    root.get
      .in("blocks" / query[String]("hash"))
      .out(compatibilityQuery[BlocksRow]("blocks by hash"))

  /** V2 Accounts endpoint definition */
  def tezosAccountsEndpoint =
    root.get
      .in("accounts" / TezosFilterFromQueryString.tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("accounts"))

  /** V2 Accounts by ID endpoint definition */
  def tezosAccountByIdEndpoint =
    root.get
      .in("accounts" / query[String]("accountId"))
      .out(compatibilityQuery[AccountResult]("account"))

  /** V2 Operation groups endpoint definition */
  def tezosOperationGroupsEndpoint =
    root.get
      .in("operation_groups" / TezosFilterFromQueryString.tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("operation groups"))

  /** V2 Operation groups by ID endpoint definition */
  def tezosOperationGroupByIdEndpoint =
    root.get
      .in("operation_groups" / query[String]("operationGroupId"))
      .out(compatibilityQuery[OperationGroupResult]("operation group"))

  /** V2 average fees endpoint definition */
  def tezosAvgFeesEndpoint =
    root.get
      .in("operations" / "avgFees" / TezosFilterFromQueryString.tezosQsFilter)
      .out(compatibilityQuery[QueryResponse]("average fees"))

  /** V2 Operations endpoint definition */
  def tezosOperationsEndpoint =
    root.get
      .in("operations" / TezosFilterFromQueryString.tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("operations"))

  private def createTags(entity: String): List[String] = List(s"Tezos $entity")

}