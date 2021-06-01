package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.metadata.PlatformPath

import scala.concurrent.ExecutionContext

/** Represents the data routes for Ethereum Blockchain */
case class EthereumDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: EthereumDataOperations,
    maxQueryResultSize: Int
)(implicit val executionContext: ExecutionContext)
    extends EthereumDataRoutesCreator {
  override val platformPath: PlatformPath = PlatformPath("ethereum")
}

/** Represents the data routes for Quorum Blockchain */
case class QuorumDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: EthereumDataOperations,
    maxQueryResultSize: Int
)(implicit val executionContext: ExecutionContext)
    extends EthereumDataRoutesCreator {
  override val platformPath: PlatformPath = PlatformPath("quorum")
}
