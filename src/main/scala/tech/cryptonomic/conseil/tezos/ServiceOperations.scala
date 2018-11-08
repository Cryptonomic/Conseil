package tech.cryptonomic.conseil.tezos

import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._


object ServiceOperations {

  final case class Counts(blocks: Int, accounts: Int, operationGroups: Int, operations: Int, fees: Int)

  final case class Network(name: String, displayName: String, platform: String, network: String)

  final case class Entity(name: String, displayName: String, count: Int, network: String)

  def getNetworks(config: Config): List[Network] = {
    config.getObject("platforms").asScala.flatMap {
      case (platform, strippedConf) =>
        strippedConf.atKey(platform).getObject(platform).asScala.map {
          case (network, _) =>
            Network(network, network.capitalize, platform, network)
        }.toList
    }.toList
  }

  def getEntities(network: String)(implicit ec: ExecutionContext): Future[List[Entity]] = {
    ApiOperations.countAll.map { counts =>
      List(
        Entity(
          name = "blocks",
          displayName = "Blocks",
          count = counts.blocks,
          network = network
        ),
        Entity(
          name = "accounts",
          displayName = "Accounts",
          count = counts.accounts,
          network = network
        ),
        Entity(
          name = "operation_groups",
          displayName = "Operation Groups",
          count = counts.operationGroups,
          network = network
        ),
        Entity(
          name = "operations",
          displayName = "Operations",
          count = counts.operations,
          network = network
        ),
        Entity(
          name = "avgFees",
          displayName = "Fees",
          count = counts.fees,
          network = network
        )
      )
    }
  }
}
