package tech.cryptonomic.conseil.common.config

import pureconfig.generic.FieldCoproductHint

trait PlatformConfigurationHint {

  /**
    * Hint, which helps us with mapping between the name of the blockchain from configuration into the specific case class.
    * This method works in a way, that key "name" from the configuration file, will be mapped into all types of `PlatformConfiguration`.
    *
    * In example, following configuration:
    * {{{
    *   {
    *     name: "tezos"
    *     network: "mainnet"
    *     enabled: true
    *   }
    * }}}
    * will be translated to:
    * {{{
    *   TezosConfiguration("mainnet", enabled = true, ???, None)
    * }}}
    */
  implicit val platformConfigurationHint: FieldCoproductHint[Platforms.PlatformConfiguration] =
    new FieldCoproductHint[Platforms.PlatformConfiguration]("name") {
      override def fieldValue(name: String): String = name.dropRight("Configuration".length).toLowerCase
    }
}

object PlatformConfigurationHint extends PlatformConfigurationHint
