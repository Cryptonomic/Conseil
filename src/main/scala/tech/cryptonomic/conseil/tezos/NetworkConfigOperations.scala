package tech.cryptonomic.conseil.tezos

import com.typesafe.config.Config
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.Network
import scala.collection.JavaConverters._

object NetworkConfigOperations {
  /**
    * Extracts networks from config file
    *
    * @param  config configuration object
    * @return list of networks from configuration
    */
  def getNetworks(config: Config): List[Network] = {
    for {
      (platform, strippedConf) <- config.getObject("platforms").asScala
      (network, _) <- strippedConf.atKey(platform).getObject(platform).asScala
    } yield Network(network, network.capitalize, platform, network)
  }.toList
}
