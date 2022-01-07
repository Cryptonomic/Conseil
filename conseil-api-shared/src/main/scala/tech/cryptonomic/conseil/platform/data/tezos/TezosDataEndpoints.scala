package tech.cryptonomic.conseil.platform.data.tezos

import sttp.tapir._
import sttp.model.StatusCode

import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.tezos.Tables._

trait TezosDataEndpoints extends ApiDataEndpoints with TezosFilterFromQueryString {

  import tech.cryptonomic.conseil.platform.data.converters._
  import tech.cryptonomic.conseil.platform.data.schemas._

  lazy val xtzEndpoints = List(
    tezosQueryEndpoint,
    // tezosBlocksEndpoint,
    tezosBlocksHeadEndpoint,
    tezosBlockByHashEndpoint,
    // tezosAccountsEndpoint,
    tezosAccountByIdEndpoint,
    // tezosOperationGroupsEndpoint,
    tezosOperationGroupByIdEndpoint
    // tezosAvgFeesEndpoint,
    // tezosOperationsEndpoint
  )

  val xtzPlatform = "tezos"

  private def root: Endpoint[Unit, Unit, Nothing, Unit, Any] = commonPath(xtzPlatform)

  def tezosQueryEndpoint = queryEndpoint(xtzPlatform)

  /** V2 Blocks endpoint definition */
  def tezosBlocksEndpoint =
    root.get
      .in("blocks" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("blocks"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Blocks head endpoint definition */
  def tezosBlocksHeadEndpoint =
    root.get
      .in("blocks" / "head")
      .out(compatibilityQuery[BlocksRow]("blocks head"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Blocks by hash endpoint definition */
  def tezosBlockByHashEndpoint =
    root.get
      .in("blocks" / query[String]("hash"))
      .out(compatibilityQuery[BlocksRow]("blocks by hash"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Accounts endpoint definition */
  def tezosAccountsEndpoint =
    root.get
      .in("accounts" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("accounts"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Accounts by ID endpoint definition */
  def tezosAccountByIdEndpoint =
    root.get
      .in("accounts" / query[String]("accountId"))
      .out(compatibilityQuery[AccountResult]("account"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Operation groups endpoint definition */
  def tezosOperationGroupsEndpoint =
    root.get
      .in("operation_groups" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("operation groups"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Operation groups by ID endpoint definition */
  def tezosOperationGroupByIdEndpoint =
    root.get
      .in("operation_groups" / query[String]("operationGroupId"))
      .out(compatibilityQuery[OperationGroupResult]("operation group"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 average fees endpoint definition */
  def tezosAvgFeesEndpoint =
    root.get
      .in("operations" / "avgFees" / tezosQsFilter)
      .out(compatibilityQuery[QueryResponse]("average fees"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Operations endpoint definition */
  def tezosOperationsEndpoint =
    root.get
      .in("operations" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("operations"))
      .errorOut(statusCode(StatusCode.NotFound))

  protected def createTags(entity: String): List[String] = List(s"Tezos $entity")

}
