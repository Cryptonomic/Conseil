package tech.cryptonomic.conseil.api.platform.data.ethereum

import tech.cryptonomic.conseil.api.platform.metadata.MetadataService
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.metadata.PlatformPath

import scala.concurrent.ExecutionContext
import tech.cryptonomic.conseil.api.platform.data.ApiDataRoutes

/** Represents the data routes for Ethereum Blockchain */
case class EthereumDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: EthereumDataOperations,
    maxQueryResultSize: Int
)(implicit val executionContext: ExecutionContext)
    extends EthereumDataRoutesCreator
    with ApiDataRoutes {
  override val platformPath: PlatformPath = PlatformPath("ethereum")
  override val ec: ExecutionContext = ExecutionContext.global
}

/** Represents the data routes for Quorum Blockchain */
case class QuorumDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: EthereumDataOperations,
    maxQueryResultSize: Int
)(implicit val executionContext: ExecutionContext)
    extends EthereumDataRoutesCreator
    with ApiDataRoutes {
  override val platformPath: PlatformPath = PlatformPath("quorum")
  override val ec: ExecutionContext = ExecutionContext.global
}
