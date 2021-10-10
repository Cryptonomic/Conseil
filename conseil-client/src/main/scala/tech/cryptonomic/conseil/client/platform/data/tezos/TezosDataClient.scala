package tech.cryptonomic.conseil.client.platform.data.tezos

import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosDataEndpoints

import endpoints4s.xhr.JsonEntitiesFromSchemas
import endpoints4s.xhr.thenable._

object TezosDataClient extends TezosDataEndpoints with Endpoints with JsonEntitiesFromSchemas {

  val eventuallyTezosQuery = tezosQueryEndpoint((String.empty, String.emtpy, ApiQuery, Some(String.empty)))
  val eventuallyTezosBlocks = tezosBlocksEndpoint((String.empty, TezosFilter, Some(String.empty)))
  val eventuallyTezosBlocksHead = tezosBlocksHeadEndpoint((String.empty, Some(String.empty)))
  val eventuallyTezosBlockByHash = tezosBlockByHashEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyTezosAccounts = tezosAccountsEndpoint((String.empty, TezosFilter) -> Some(String.empty))
  val eventuallyTezosAccountById = tezosAccountByIdEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyTezosOperationGroups = tezosOperationGroupsEndpoint((String.empty, TezosFilter) -> Some(String.empty))
  val eventuallyTezosOperationGroupById =
    tezosOperationGroupByIdEndpoint((String.empty, TezosFilter) -> Some(String.empty))
  val eventuallyTezosAvgFees = tezosAvgFeesEndpoint((String.empty, TezosFilter) -> Some(String.empty))
  val eventuallyTezosOperations = tezosOperationsEndpoint((String.empty, TezosFilter) -> Some(String.empty))
}
