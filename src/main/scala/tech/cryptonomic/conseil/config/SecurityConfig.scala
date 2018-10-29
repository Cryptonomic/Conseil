package tech.cryptonomic.conseil.config

object SecurityConfig {

  /** creates security data from configuration */
  def apply(): Either[pureconfig.error.ConfigReaderFailures, SecurityApi] =
    pureconfig.loadConfig[SecurityApi](namespace = "security.apiKeys.keys")

  final case class SecurityApi(keys: Set[String]) extends AnyVal with Product with Serializable {
    /**
      * Determines whether a given API key is valid.
      * @param candidateApiKey  The given API key
      * @return                 True is valid, false otherwise.
      */
    def validateApiKey(candidateApiKey: String): Boolean = keys(candidateApiKey)
  }

}
