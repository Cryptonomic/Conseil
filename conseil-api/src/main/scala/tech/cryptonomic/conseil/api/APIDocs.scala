package tech.cryptonomic.conseil.api

import cats.effect.IO
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUI

import tech.cryptonomic.conseil.api.platform.discovery.PlatformDiscoveryEndpoints
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataEndpoints
// import tech.cryptonomic.conseil.api.platform.data.ethereum.{EthereumDataEndpoints, QuorumDataEndpoints}
// import tech.cryptonomic.conseil.api.platform.data.bitcoin.BitcoinDataEndpoints

trait APIDocs
    extends PlatformDiscoveryEndpoints
    // with EthereumDataEndpoints
    // with QuorumDataEndpoints
    // with BitcoinDataEndpoints {
    with TezosDataEndpoints {

  private lazy val allPlatformEndpoints =
    discoveryEndpoints ++ xtzEndpoints
  // discoveryEndpoints ++ xtzEndpoints ++ ethEndpoints ++ quorumEndpoints ++ btcEndpoints

  private lazy val docs: OpenAPI =
    OpenAPIDocsInterpreter().toOpenAPI(protocol.appInfo :: allPlatformEndpoints, "Conseil API", "1.0")
  lazy val docsRoute: List[ServerEndpoint[Any, IO]] = SwaggerUI[IO](docs.toYaml)

}
