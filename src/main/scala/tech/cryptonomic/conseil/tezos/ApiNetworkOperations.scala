package tech.cryptonomic.conseil.tezos

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{complete, provide}
import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.NetworkConfigOperations

import scala.concurrent.ExecutionContext
import scala.util.Try

object ApiNetworkOperations {
  def apply(config: Config, ec: ExecutionContext): ApiNetworkOperations = new ApiNetworkOperations(config, ec)
}

class ApiNetworkOperations(config: Config, ec: ExecutionContext) {
  private lazy val apiOperationsMap: Map[(String, String), ApiOperations] = getDatabaseMap(ApiOperations.apply)

  private lazy val apiFilteringMap: Map[(String, String), DatabaseApiFiltering] = getDatabaseMap { db =>
    new DatabaseApiFiltering {
      override def asyncApiFiltersExecutionContext: ExecutionContext = ec

      override def dbHandle = db
    }
  }

  def getApiOperations(platform: String, network: String): Directive1[ApiOperations] = {
    apiOperationsMap.get((platform, network)) match {
      case Some(value) => provide(value)
      case None => complete(StatusCodes.NotFound)
    }
  }

  def getApiFiltering(platform: String, network: String): Directive1[DatabaseApiFiltering] = {
    apiFilteringMap.get((platform, network)) match {
      case Some(value) => provide(value)
      case None => complete(StatusCodes.NotFound)
    }
  }

  private def getDatabaseMap[A](fun: Database => A): Map[(String, String), A] = {
    NetworkConfigOperations.getPlatforms(config).flatMap { platform =>
      NetworkConfigOperations.getNetworks(config).map { network =>
        (platform, network.name) -> Try {
          fun(Database.forConfig(s"databases.$platform.${network.name}.conseildb"))
        }.toOption
      }
    }.filter(_._2.isDefined).toMap.mapValues(_.get)
  }
}
