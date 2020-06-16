package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}

case class BitcoinDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: BitcoinDataOperations,
    maxQueryResultSize: Int
)(
    implicit apiExecutionContext: ExecutionContext
) extends ApiDataRoutes
    with LazyLogging
    with BitcoinDataHelpers {

  import cats.instances.either._
  import cats.instances.future._
  import cats.syntax.bitraverse._

  /** V2 Route implementation for query endpoint */
  override val postRoute: Route = queryEndpoint.implementedByAsync {
    case ((platform, network, entity), apiQuery, _) =>
      val path = EntityPath(entity, NetworkPath(network, PlatformPath(platform)))

      pathValidation(path) {
        apiQuery
          .validate(path, metadataService, metadataConfiguration)
          .flatMap { validationResult =>
            validationResult.map { validQuery =>
              operations.queryWithPredicates(platform, entity, validQuery.withLimitCap(maxQueryResultSize)).map {
                queryResponses =>
                  QueryResponseWithOutput(queryResponses, validQuery.output)
              }
            }.left.map(Future.successful).bisequence
          }
          .map(Some(_))
      }
  }

  /** V2 Route implementation for blocks endpoint */
  private val blocksRoute: Route = blocksEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        operations
          .queryWithPredicates(platform, "blocks", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Option(_))
      }
  }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute: Route = blocksHeadEndpoint.implementedByAsync {
    case (platform, network, _) =>
      platformNetworkValidation(platform, network) {
        operations.fetchLatestBlock()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute: Route = blockByHashEndpoint.implementedByAsync {
    case ((platform, network, hash), _) =>
      platformNetworkValidation(platform, network) {
        val filter = BitcoinFilter(blockIDs = Set(hash)) //TODO Verify that it works. Maybe we need to write custom method
        operations
          .queryWithPredicates(platform, "blocks", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(_.headOption)
      }
  }

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute: Route = transactionsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        operations
          .queryWithPredicates(platform, "transactions", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Some(_))
      }
  }

  /** V2 Route implementation for inputs endpoint */
  private val inputsRoute: Route = inputsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        operations
          .queryWithPredicates(platform, "inputs", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Some(_))
      }
  }

  /** V2 Route implementation for outputs endpoint */
  private val outputsRoute: Route = inputsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        operations
          .queryWithPredicates(platform, "outputs", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Some(_))
      }
  }

  /** V2 concatenated routes */
  override val getRoute: Route =
    concat(
      blocksHeadRoute,
      blockByHashRoute,
      blocksRoute,
      transactionsRoute,
      inputsRoute,
      outputsRoute
    )

  /** Function for validation of the platform and network with flatten */
  protected def platformNetworkValidation[A](platform: String, network: String)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    pathValidation(NetworkPath(network, PlatformPath(platform)))(operation)

  protected def pathValidation[A](path: metadata.Path)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    if (metadataService.exists(path))
      operation
    else
      Future.successful(None)

}
