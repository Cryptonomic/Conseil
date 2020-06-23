package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import io.circe._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataHelpers
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.generic.BitcoinDataEndpoints

/** Represents helper for Data Endpoints that can be used to implement custom encoder for Bitcoin specific types */
private[bitcoin] class BitcoinDataHelpers extends BitcoinDataEndpoints with ApiDataHelpers {

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder: PartialFunction[Any, Json] = PartialFunction.empty

}
