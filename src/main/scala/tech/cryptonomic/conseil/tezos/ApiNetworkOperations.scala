package tech.cryptonomic.conseil.tezos

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{complete, provide}
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.db.DatabaseApiFiltering

import scala.concurrent.ExecutionContext


object ApiNetworkOperations {
  private lazy val networkOperationsMap: Map[String, Database] = getDatabaseMap

  def apply(): ApiNetworkOperations = new ApiNetworkOperations(networkOperationsMap)

  def getDatabaseMap: Map[String, Database] = {
    val config: Config = ConfigFactory.load()
    PlatformDiscoveryOperations().getNetworks(config).map { network =>
      network.name -> Database.forConfig(s"db.$network.conseildb")
    }.toMap
  }
}

class ApiNetworkOperations(networkOperationsMap: Map[String, Database]) {
  def getApiOperations(network: String): Directive1[ApiOperations] = {
    networkOperationsMap.get(network) match {
      case Some(value) => provide(ApiOperations(value))
      case None => complete(StatusCodes.NotFound)
    }
  }

  def getApiFiltering(network: String, apiFilteringExecutionContext: ExecutionContext): Directive1[DatabaseApiFiltering] = {
    networkOperationsMap.get(network) match {
      case Some(value) => provide(DatabaseApiFiltering(value)(apiFilteringExecutionContext))
      case None => complete(StatusCodes.NotFound)
    }
  }

}
