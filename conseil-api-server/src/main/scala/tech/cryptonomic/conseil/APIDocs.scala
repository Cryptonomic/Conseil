package tech.cryptonomic.conseil

import cats.effect.IO
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.swagger.SwaggerUI

import tech.cryptonomic.conseil.platform.discovery.PlatformDiscoveryEndpoints
import tech.cryptonomic.conseil.platform.data.tezos.TezosDataEndpoints
import tech.cryptonomic.conseil.platform.data.ethereum.{EthereumDataEndpoints, QuorumDataEndpoints}
import tech.cryptonomic.conseil.platform.data.bitcoin.BitcoinDataEndpoints
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.Endpoint

trait APIDocs
    extends PlatformDiscoveryEndpoints
    with TezosDataEndpoints
    with EthereumDataEndpoints
    with QuorumDataEndpoints
    with BitcoinDataEndpoints {

  private val allPlatformEndpoints: List[Endpoint[Unit, _, _, _, Any]] = discoveryEndpoints /* ++ xtzEndpoints ++ ethEndpoints ++ quorumEndpoints ++ btcEndpoints */
  private val docs: OpenAPI = OpenAPIDocsInterpreter()
    .toOpenAPI(protocol.appInfo :: allPlatformEndpoints, "My App", "1.0")
  val docsRoute = SwaggerUI[IO](docs.toYaml)
}
