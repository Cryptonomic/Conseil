package tech.cryptonomic.conseil.util

import com.typesafe.config._
import com.typesafe.scalalogging.LazyLogging
import scala.util.Try
import tech.cryptonomic.conseil.config.Platforms._
import tech.cryptonomic.conseil.config.HttpStreamingConfiguration

object ConfigUtil {

  //TODO: make it work with current config
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

  object Pureconfig extends LazyLogging {
    import pureconfig.{ConfigReader, ConfigFieldMapping, CamelCase}
    import pureconfig.error.{ConfigReaderFailures, ExceptionThrown, FailureReason, ThrowableFailure}
    import pureconfig.generic.auto._
    import pureconfig.generic.ProductHint
    import scala.collection.mutable
    import scala.collection.JavaConverters._

    /** converts multiple read failures to a single, generic, FailureReason */
    val reasonFromReadFailures = (failures: ConfigReaderFailures) =>
      new FailureReason { override val description = failures.toList.map(_.description).mkString(" and ") }

    /** extract a custom class type from the generic lightbend-config value failing with a `FailureReason` */
    def readAndFailWithFailureReason[T](value: ConfigValue)(implicit reader: ConfigReader[T]): Either[FailureReason, T] =
      reader.from(value).left.map(reasonFromReadFailures)

    /**
      * Check all results that may have failed, returning an `Either` that will hold a single failure if there's any.
      * In case of all successful parsing, the results are reduced with the passed-in function
      * @param readResults output of some ConfigReader parsing
      * @param reduce aggregator function that will be applied to successful results
      * @tparam T the input parsing expected type
      * @tparam R the final outcome type in case of no failed input
      */
    def foldReadResults[T, R](readResults: Traversable[Either[FailureReason, T]])(reduce: Traversable[T] => R) = {
      //elements might have failed, collect all failures
      val failureDescription = readResults.collect{ case Left(f) => f.description }.mkString(" and ")
      if (failureDescription.nonEmpty) logger.warn("Can't correctly read platform configuration, reasons are: {}", failureDescription)
      //test for failures, and if there's none, we can safely read the Right values
      Either.cond(
        test = !readResults.exists(_.isLeft),
        right = reduce(readResults.map(_.right.get)),
        left = new FailureReason{ override val description = failureDescription }
      )
    }

    /** pureconfig reader for lists of tezos configurations, i.e. one for each network */
    implicit val tezosConfigurationsReader: ConfigReader[List[TezosConfiguration]] =
      ConfigReader[ConfigObject].emap {
        obj =>
          //applies convention to uses CamelCase when reading config fields
          implicit def hint = ProductHint[TezosNodeConfiguration](ConfigFieldMapping(CamelCase, CamelCase))

          val availableNetworks = obj.keySet.asScala
          val parsed: mutable.Set[Either[FailureReason, TezosConfiguration]] = availableNetworks.map {
            network =>
              //try to read the node subconfig as an Either, might be missing or fail to be an object
              Try(obj.toConfig.getObject(s"$network.node"))
                .toEither
                .left.map(ExceptionThrown)
                .flatMap(readAndFailWithFailureReason[TezosNodeConfiguration])
                //creates the whole config entry
                .map(TezosConfiguration(network, _))
            }
            foldReadResults(parsed) {
              _.toList
            }
      }

    /** pureconfig reader for undefined Platform configurations */
    implicit val unknownPlatformReader: ConfigReader[List[UnknownPlatformConfiguration]] =
      ConfigReader[ConfigObject].map(_.keySet.asScala.toList.map(key => UnknownPlatformConfiguration(key)))


    /** pureconfig reader that converts a top-level platforms definition to a structured mapping */
    implicit val platformsConfigurationsReader: ConfigReader[PlatformsConfiguration] =
      ConfigReader[ConfigObject].emap {
        confObj =>
          //polymorphic config reader for each platform, that will use the appropriate ConfigurationType reader for each platform type
          def extractConfigList[P <: BlockchainPlatform]
            (platform: P)
            (implicit reader: ConfigReader[List[platform.ConfigurationType]]): Either[FailureReason, (P, List[PlatformConfiguration])] =
            readAndFailWithFailureReason[List[platform.ConfigurationType]](confObj.get(platform.name)).map(platform -> _)

          //converts each string key to a proper platform type
          val availablePlatforms = confObj.keySet.asScala.map(BlockchainPlatform.fromString)

          val parsed: mutable.Set[Either[FailureReason, (BlockchainPlatform, List[PlatformConfiguration])]] =
            availablePlatforms.map {
              case Tezos => extractConfigList(Tezos)
              case p @ UnknownPlatform(_) => extractConfigList(p)
            }

          foldReadResults(parsed) {
            platformEntries => PlatformsConfiguration(platformEntries.toMap)
          }
      }

      /**
        * Reads a specific entry in the configuration file, to create a valid akka-http client host-pool configuration
        * @param namespace the path where the custom configuration will be searched-for
        */
      def loadAkkaStreamingClientConfig(namespace: String): Either[ConfigReaderFailures, HttpStreamingConfiguration] = {

        //this is where akka searches for the config entry for host connection pool
        val referenceHostPoolEntryPath = "akka.http.host-connection-pool"

        //read a conseil-specific entry into the expected path for the http streaming client host pool
        def loadHostPoolConfig(rootConfig: Config): Either[ConfigReaderFailures, Config] =
          Try(rootConfig.getConfig(namespace)
                .atPath(referenceHostPoolEntryPath) //puts the config entry where expected by akka
                .withFallback(rootConfig) //adds default values, where not overriden
                .ensuring(
                  endConfig =>
                    //verifies all expected entries are there
                    Try(endConfig.checkValid(rootConfig.getConfig(referenceHostPoolEntryPath), referenceHostPoolEntryPath)).isSuccess
                )
            ).toEither
            .left.map{
              //wraps the error into pureconfig's one
              t => ConfigReaderFailures(new ThrowableFailure(t, None))
            }

        //compose the configuration reading outcomes
        for {
          akkaConf <- pureconfig.loadConfig[Config]
          tezosClientConfig <- loadHostPoolConfig(akkaConf)
        } yield HttpStreamingConfiguration(tezosClientConfig)
      }

  }

}