package tech.cryptonomic.conseil.api

import cats.effect.IO
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.swagger.SwaggerUI

import tech.cryptonomic.conseil.api.platform.discovery.PlatformDiscoveryEndpoints
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataEndpoints
import tech.cryptonomic.conseil.api.platform.data.ethereum.{EthereumDataEndpoints, QuorumDataEndpoints}
import tech.cryptonomic.conseil.api.platform.data.bitcoin.BitcoinDataEndpoints

trait APIDocs
    extends PlatformDiscoveryEndpoints
    with TezosDataEndpoints
    with EthereumDataEndpoints
    with QuorumDataEndpoints
    with BitcoinDataEndpoints {

  private lazy val allPlatformEndpoints =
    discoveryEndpoints ++ xtzEndpoints ++ ethEndpoints ++ quorumEndpoints ++ btcEndpoints

  private lazy val docs: OpenAPI =
    OpenAPIDocsInterpreter().toOpenAPI(protocol.appInfo :: allPlatformEndpoints, "Conseil API", "1.0")
  lazy val docsRoute = SwaggerUI[IO](docs.toYaml)

}
