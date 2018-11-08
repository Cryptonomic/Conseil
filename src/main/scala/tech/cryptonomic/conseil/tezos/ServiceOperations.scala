package tech.cryptonomic.conseil.tezos

import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._


object ServiceOperations {

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
}
