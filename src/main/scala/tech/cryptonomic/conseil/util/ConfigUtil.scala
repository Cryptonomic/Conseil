package tech.cryptonomic.conseil.util

import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

import java.util.HashMap

object ConfigUtil {

  private val conf = ConfigFactory.load

  /**
    * Output Map, keys being blockchain platforms, values being comma and space
    * separated string of their networks
    * @return platform, network Map
    */
  def parsePlatformsAndNetworks(): Map[String, String] = {
    val obj = conf.getObject("platforms").asScala.toMap
    val platformsAndNetworks = obj.map { pair =>
      val platforms = pair._1
      val networks = pair._2
        //convert map to network strings
        .unwrapped().asInstanceOf[HashMap[String, String]].asScala.keySet
        //collect using concatenation with commas
        .foldLeft("")((acc, network) => acc + network + ", ")
        //drop the last comma and space
        .dropRight(2)
      (platforms, networks)
    }
    platformsAndNetworks
  }

  /**
    * Given a specific platform, output comma and space separated string of its
    * networks
    * @param platform Blockchain platform
    * @return
    */
  def parseNetworksForAGivenPlatform(platform: String): String = {
    val platformsAndNetworks = parsePlatformsAndNetworks()
    platformsAndNetworks(platform)
  }

  /**
    * Return newline and tab separated string represented in the following
    * format:
    * <platform>: <network_one>, <network_two>...\n
    * @return
    */
  def outputPlatformsAndNetworks(): String = {
    val platformsAndNetworks = parsePlatformsAndNetworks()
    //collect platform and it's list of networks into output string, separated
    //by newlines and tabs
    platformsAndNetworks.foldLeft(""){ (acc, platformsAndNetworksPair) =>
      val platform = platformsAndNetworksPair._1
      val networks  = platformsAndNetworksPair._2
      acc + platform + ": " + networks + "\n\t"
    }
  }

}
