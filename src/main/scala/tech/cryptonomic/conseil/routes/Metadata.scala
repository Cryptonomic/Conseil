package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosMetadataOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.{ConfigUtil, RouteHandling}

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object Metadata {
  def apply(config: Config)(implicit apiExecutionContext: ExecutionContext): Metadata =
    new Metadata(config)
}

/**
  * Platform discovery routes.
  *
  * @param config              configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class Metadata(config: Config)
  (implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RouteHandling {
  val route: Route =
    get {
      pathPrefix("platforms") {
        complete(toJson(ConfigUtil.getPlatforms(config)))
      } ~
        pathPrefix(Segment) { platform =>
          pathPrefix("networks") {
            pathEnd {
              complete(toJson(ConfigUtil.getNetworks(config, platform)))
            }
          } ~ pathPrefix(Segment) { network =>
            validatePlatformAndNetwork(config, platform, network) {
              pathPrefix("entities") {
                pathEnd {
                  completeWithJson(TezosMetadataOperations.getEntities(network))
                }
              } ~ pathPrefix(Segment) { entity =>
                validateEntity(entity) {
                  pathPrefix("attributes") {
                    pathEnd {
                      completeWithJson(TezosMetadataOperations.getTableAttributes(entity))
                    }
                  } ~ pathPrefix(Segment) { attribute =>
                    pathEnd {
                      completeWithJson(TezosMetadataOperations.listAttributeValues(entity, attribute))
                    } ~ pathPrefix(Segment) { filter =>
                      pathEnd {
                        completeWithJson(TezosMetadataOperations.listAttributeValues(entity, attribute, Some(filter)))
                      }
                    }
                  }
                }
              }
            }
          }
        }
    }
}
