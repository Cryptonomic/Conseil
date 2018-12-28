package tech.cryptonomic.conseil.util

import com.typesafe.config.Config
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Network, Platform}

import scala.collection.JavaConverters._

object ConfigUtil {
  /**
    * Extracts networks from config file
    *
    * @param  config configuration object
    * @param  platform platform name
    * @return list of networks from configuration
    */
  def getNetworks(config: Config, platform: String): List[Network] = {
    for {
      (platformName, strippedConf) <- config.getObject("platforms").asScala
      if platformName == platform
      (network, _) <- strippedConf.atKey(platformName).getObject(platformName).asScala
    } yield Network(network, network.capitalize, platformName, network)
  }.toList


  /**
    * Extracts platforms from config file
    *
    * @param  config configuration object
    * @return list of platforms from configuration
    */
  def getPlatforms(config: Config): List[Platform] = {
    for {
      (platform, _) <- config.getObject("platforms").asScala
    } yield Platform(platform, platform.capitalize)
  }.toList

}
