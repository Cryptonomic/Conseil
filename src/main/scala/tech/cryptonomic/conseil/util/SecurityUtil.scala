package tech.cryptonomic.conseil.util

import com.typesafe.config.ConfigFactory

/**
  * Utility functions pertaining to authorization and authentication.
  */
object SecurityUtil {

  val conf = ConfigFactory.load

  /**
    * Determines whether a given API key is valid.
    * @param candidateApiKey
    * @return
    */
  def validateApiKey(candidateApiKey: String) = conf.getStringList("security.apiKeys.keys").contains(candidateApiKey)

}
