package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.util.JsonUtil._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object Service {
  def apply(config: Config)(implicit apiExecutionContext: ExecutionContext): Service = new Service(config)
}

class Service(config: Config)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging {
  val route: Route =
    get {
      pathPrefix("networks") {
        pathEnd {
          complete(toJson(getNetworks))
        } ~ pathPrefix(Segment) { network =>
          pathPrefix("entities") {
            pathEnd {
              completeWithJson(getEntities(network))
            }
          }
        }
      }
    }

  private def getNetworks: List[Network] = {
    config.getObject("platforms").asScala.flatMap {
      case (platform, strippedConf) =>
        strippedConf.atKey(platform).getObject(platform).asScala.map {
          case (network, _) =>
            Network(network, network.capitalize, platform, network)
        }.toList
    }.toList
  }

  implicit private val jsonMarshaller: ToEntityMarshaller[JsonString] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose((_: JsonString).json)
      .wrap(MediaTypes.`application/json`)(identity _)

  /* converts the future value to [[JsonString]] and completes the call */
  private def completeWithJson[T](futureValue: Future[T]): StandardRoute =
    complete(futureValue.map(toJson[T]))

  private def getEntities(network: String): Future[List[Entity]] = {
    val blocksCountFut = ApiOperations.countBlocks
    val accountsCountFut = ApiOperations.countAccounts
    val operationGroupsCountFut = ApiOperations.countBlocks
    val operationsCountFut = ApiOperations.countAccounts
    val feesCountFut = ApiOperations.countFees

    for {
      blocksCount <- blocksCountFut
      accountsCount <- accountsCountFut
      operationGroupsCount <- operationGroupsCountFut
      operationsCount <- operationsCountFut
      feesCount <- feesCountFut
    } yield
      List(
        Entity(
          name = "blocks",
          displayName = "Blocks",
          count = blocksCount,
          network = network
        ),
        Entity(
          name = "accounts",
          displayName = "Accounts",
          count = accountsCount,
          network = network
        ),
        Entity(
          name = "operation_groups",
          displayName = "Operation Groups",
          count = operationGroupsCount,
          network = network
        ),
        Entity(
          name = "operations",
          displayName = "Operations",
          count = operationsCount,
          network = network
        ),
        Entity(
          name = "avgFees",
          displayName = "Fees",
          count = feesCount,
          network = network
        )
      )
  }

  case class Network(name: String, displayName: String, platform: String, network: String)

  case class Entity(name: String, displayName: String, count: Int, network: String)

}
