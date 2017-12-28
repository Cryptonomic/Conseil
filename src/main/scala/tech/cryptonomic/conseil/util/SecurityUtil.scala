package tech.cryptonomic.conseil.util

import com.typesafe.config.ConfigFactory

/**
  * Utility functions pertaining to authorization and authentication.
  */
object SecurityUtil {

  private val conf = ConfigFactory.load

  /**
    * Determines whether a given API key is valid.
    * @param candidateApiKey  The given API key
    * @return                 True is valid, false otherwise.
    */
  def validateApiKey(candidateApiKey: String): Boolean = conf.getStringList("security.apiKeys.keys").contains(candidateApiKey)

}
