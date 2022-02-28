package tech.cryptonomic.conseil.api.platform.data.tezos

import sttp.tapir._
import sttp.model.StatusCode

import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.api.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.tezos.Tables._

trait TezosDataEndpoints extends ApiDataEndpoints with TezosFilterFromQueryString {

  import tech.cryptonomic.conseil.api.platform.data.converters._
  import tech.cryptonomic.conseil.api.platform.data.schemas._

  lazy val xtzEndpoints = List(
    tezosQueryEndpoint,
    tezosBlocksHeadEndpoint,
    tezosBlockByHashEndpoint,
    tezosAccountsEndpoint,
    tezosAccountByIdEndpoint,
    tezosOperationGroupsEndpoint,
    tezosOperationGroupByIdEndpoint,
    tezosAvgFeesEndpoint,
    tezosOperationsEndpoint
  )

  val xtzPlatform = "tezos"

  private def common =
    infallibleEndpoint.get
      // .securityIn(auth.apiKey(header[Option[String]]("apiKey")))
      .in("v2" / "data" / xtzPlatform / query[String]("network"))
      .errorOut(statusCode(StatusCode.NotFound))

  def tezosQueryEndpoint = queryEndpoint(xtzPlatform)

  /** V2 Blocks endpoint definition */
  def tezosBlocksEndpoint =
    common
      .in("blocks" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("blocks"))

  /** V2 Blocks head endpoint definition */
  def tezosBlocksHeadEndpoint =
    common
      .in("blocks" / "head")
      .out(compatibilityQuery[BlocksRow]("blocks head"))

  /* Up until here */

  /** V2 Blocks by hash endpoint definition */
  def tezosBlockByHashEndpoint =
    common
      .in("blocks" / query[String]("hash"))
      .out(compatibilityQuery[BlockResult]("blocks by hash"))

  /** V2 Accounts endpoint definition */
  def tezosAccountsEndpoint =
    common
      .in("accounts" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("accounts"))

  /** V2 Accounts by ID endpoint definition */
  def tezosAccountByIdEndpoint =
    common
      .in("accounts" / query[String]("accountId"))
      .out(compatibilityQuery[AccountResult]("account"))

  /** V2 Operation groups endpoint definition */
  def tezosOperationGroupsEndpoint =
    common
      .in("operation_groups" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("operation groups"))

  /** V2 Operation groups by ID endpoint definition */
  def tezosOperationGroupByIdEndpoint =
    common
      .in("operation_groups" / query[String]("operationGroupId"))
      .out(compatibilityQuery[OperationGroupResult]("operation group"))

  /** V2 average fees endpoint definition */
  def tezosAvgFeesEndpoint =
    common
      .in("operations" / "avgFees" / tezosQsFilter)
      .out(compatibilityQuery[QueryResponse]("average fees"))

  /** V2 Operations endpoint definition */
  def tezosOperationsEndpoint =
    common
      .in("operations" / tezosQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("operations"))

  protected def createTags(entity: String): List[String] = List(s"Tezos $entity")

}
