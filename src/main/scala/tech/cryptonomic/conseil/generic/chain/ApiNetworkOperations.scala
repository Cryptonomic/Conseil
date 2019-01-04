package tech.cryptonomic.conseil.generic.chain

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{complete, provide}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.ExecutionContext
import scala.util.Try

/** Companion object for apply in ApiNetworkOperations */
object ApiNetworkOperations {
  def apply(config: PlatformsConfiguration, ec: ExecutionContext): ApiNetworkOperations = new ApiNetworkOperations(config, ec)
}

/** Class for storing object which require DB */
class ApiNetworkOperations(config: PlatformsConfiguration, ec: ExecutionContext) {

  /** map storing ApiOperations objects for given (platform, network) */
  private lazy val apiOperationsMap: Map[(String, String), ApiOperations] = getDatabaseMap(ApiOperations.apply)

  /** map storing DatabaseApiFiltering objects for given (platform, network) */
  private lazy val apiFilteringMap: Map[(String, String), DatabaseApiFiltering] = getDatabaseMap { db =>
    new DatabaseApiFiltering {
      override def asyncApiFiltersExecutionContext: ExecutionContext = ec

      override def dbHandle = db
    }
  }

  /** Directive for providing correct ApiOperations per platform/network
    * @param platform name of the platform
    * @param network name of the network
    * @return route with ApiOperations object
    */
  def getApiOperations(platform: String, network: String): Directive1[ApiOperations] = {
    apiOperationsMap.get((platform, network)) match {
      case Some(value) => provide(value)
      case None => complete(StatusCodes.NotFound)
    }
  }

  /** Directive for providing correct DatabaseApiFiltering per platform/network
    * @param platform name of the platform
    * @param network name of the network
    * @return route with DatabaseApiFiltering object
    */
  def getApiFiltering(platform: String, network: String): Directive1[DatabaseApiFiltering] = {
    apiFilteringMap.get((platform, network)) match {
      case Some(value) => provide(value)
      case None => complete(StatusCodes.NotFound)
    }
  }

  /** Helper method for creating map from (network, platform) to given type of object */
  private def getDatabaseMap[A](fun: Database => A): Map[(String, String), A] = {
    for {
      platform <- ConfigUtil.getPlatforms(config)
      network <- ConfigUtil.getNetworks(config, platform.name)
      db <- Try(fun(Database.forConfig(s"databases.${platform.name}.${network.name}.conseildb"))).toOption
    } yield (platform.name, network.name) -> db
  }.toMap

}
