package tech.cryptonomic.conseil.client.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.BitcoinDataEndpoints

import endpoints4s.xhr.JsonEntitiesFromSchemas
import endpoints4s.xhr.thenable._

object BitcoinDataClient extends BitcoinDataEndpoints with Endpoints with JsonEntitiesFromSchemas {

  val eventuallyBitcoinQuery = bitcoinQueryEndpoint(String.empty, String.empty, ApiQuery, Some(String.empty))
  val eventuallyBitcoinBlocks = bitcoinBlocksEndpoint((String.empty, BitcoinFilter) -> Some(String.empty))
  val eventuallyBitcoinBlocksHead = bitcoinBlocksHeadEndpoint(String.empty -> Some(String.empty))
  val eventuallyBitcoinBlockByHash = bitcoinBlockByHashEndpoint((String.empty, String.emtpy) -> Some(String.empty))
  val eventuallyBitcoinTransactions = bitcoinTransactionsEndpoint((String.empty, BitcoinFilter) -> Some(String.empty))
  val eventuallyBitcoinTransactionById =
    bitcoinTransactionByIdEndpoint((String.empty, String.emtpy) -> Some(String.empty))
  val eventuallyBitcoinInputs = bitcoinInputsEndpoint((String.empty, BitcoinFilter) -> Some(String.empty))
  val eventuallyBitcoinOutputs = bitcoinOutputsEndpoint((String.empty, BitcoinFilter) -> Some(String.empty))
  val eventuallyBitcoinAccounts = bitcoinAccountsEndpoint((String.empty, BitcoinFilter) -> Some(String.empty))
  val eventuallyBitcoinAccountByAddress =
    bitcoinAccountByAddressEndpoint((String.empty, String.empty) -> Some(String.empty))
}
