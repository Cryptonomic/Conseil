package tech.cryptonomic.conseil.common.util

import com.github.ghik.silencer.silent
import com.typesafe.config._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.common.config.HttpStreamingConfiguration
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Network, Platform}

import scala.util.Try

object ConfigUtil {

  /**
    * Extracts networks from config file
    *
    * @param  config a mapping from platform to all available networks configurations
    * @return list of networks from configuration
    */
  def getNetworks(config: PlatformsConfiguration, platformName: String): List[Network] =
    for {
      (platform, configs) <- config.platforms.toList
      if platform == BlockchainPlatform.fromString(platformName)
      networkConfiguration <- configs
    } yield {
      val network = networkConfiguration.network
      Network(network, network.capitalize, platform.name, network)
    }

  /**
    * Extracts platforms from config file
    *
    * @param  config configuration object
    * @return list of platforms from configuration
    */
  def getPlatforms(config: PlatformsConfiguration): List[Platform] =
    for {
      (platform, _) <- config.platforms.toList
    } yield Platform(platform.name, platform.name.capitalize)

  object Pureconfig extends LazyLogging {

    import pureconfig.error.{ConfigReaderFailures, ExceptionThrown, FailureReason, ThrowableFailure}
    import pureconfig.generic.ProductHint
    import pureconfig.generic.auto._
    import pureconfig.{CamelCase, ConfigFieldMapping, ConfigReader}

    import scala.collection.JavaConverters._
    import scala.collection.mutable

    /** converts multiple read failures to a single, generic, FailureReason */
    val reasonFromReadFailures = (failures: ConfigReaderFailures) =>
      new FailureReason {
        override val description = failures.toList.map(_.description).mkString(" ")
      }

    /** extract a custom class type from the generic lightbend-config value failing with a `FailureReason` */
    def readAndFailWithFailureReason[T](
        value: ConfigValue
    )(implicit reader: ConfigReader[T]): Either[FailureReason, T] =
      reader.from(value).left.map(reasonFromReadFailures)

    /**
      * Check all results that may have failed, returning an `Either` that will hold a single failure if there's any.
      * In case of all successful parsing, the results are reduced with the passed-in function
      *
      * @param readResults output of some ConfigReader parsing
      * @param reduce      aggregator function that will be applied to successful results
      * @tparam T the input parsing expected type
      * @tparam R the final outcome type in case of no failed input
      */
    def foldReadResults[T, R](readResults: Traversable[Either[FailureReason, T]])(reduce: Traversable[T] => R) = {
      //elements might have failed, collect all failures
      val failureDescription = readResults.collect { case Left(f) => f.description }.mkString(" ")
      if (failureDescription.nonEmpty)
        logger.warn("Can't correctly read platform configuration, reasons are: {}", failureDescription)
      //test for failures, and if there's none, we can safely read the Right values
      Either.cond(
        test = !readResults.exists(_.isLeft),
        right = reduce(readResults.map(_.right.get)),
        left = new FailureReason {
          override val description = failureDescription
        }
      )
    }

    /** pureconfig reader for lists of tezos configurations, i.e. one for each network.
      * Take care: this will not load any TNS definition from the config file, since
      * it's supposed to be used only to decode the "platforms" configuration section, which
      * doesn't include the "tns" definition.
      */
    implicit val tezosConfigurationsReader: ConfigReader[List[TezosConfiguration]] =
      ConfigReader[ConfigObject].emap { obj =>
        //applies convention to uses CamelCase when reading config fields
        @silent("local method hint in value")
        implicit def hint[T: ConfigReader] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

        val availableNetworks = obj.keySet.asScala
        val config = obj.toConfig()
        val parsed: mutable.Set[Either[FailureReason, TezosConfiguration]] = availableNetworks.map { network =>
          //try to read the node and tns subconfigs as an Either, might be missing or fail to be an object
          def findObject[T: ConfigReader](readValue: => ConfigValue) =
            Try(readValue).toEither.left.map(ExceptionThrown).flatMap(readAndFailWithFailureReason[T])

          val path = s"$network.node"
          findObject[TezosNodeConfiguration](config.getObject(path))
            .map(node => TezosConfiguration(network, node, tns = None))

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
      ConfigReader[ConfigObject].emap { confObj =>
        //polymorphic config reader for each platform, that will use the appropriate ConfigurationType reader for each platform type
        def extractConfigList[P <: BlockchainPlatform](platform: P)(
            implicit reader: ConfigReader[List[platform.ConfigurationType]]
        ): Either[FailureReason, (P, List[PlatformConfiguration])] =
          readAndFailWithFailureReason[List[platform.ConfigurationType]](confObj.get(platform.name)).map(platform -> _)

        //converts each string key to a proper platform type
        val availablePlatforms = confObj.keySet.asScala.map(BlockchainPlatform.fromString)

        val parsed: mutable.Set[Either[FailureReason, (BlockchainPlatform, List[PlatformConfiguration])]] =
          availablePlatforms.map {
            case Tezos => extractConfigList(Tezos)
            case p @ UnknownPlatform(_) => extractConfigList(p)
          }

        foldReadResults(parsed) { platformEntries =>
          PlatformsConfiguration(platformEntries.toMap)
        }
      }

    def loadPlatformConfiguration(
        platform: String,
        network: String
    ): Either[ConfigReaderFailures, PlatformConfiguration] = {
      import pureconfig.loadConfig
      BlockchainPlatform.fromString(platform) match {
        case Tezos =>
          for {
            node <- loadConfig[TezosNodeConfiguration](namespace = s"platforms.$platform.$network.node")
            tns <- loadConfig[Option[TNSContractConfiguration]](namespace = s"tns.$network")
          } yield TezosConfiguration(network, node, tns)
        case UnknownPlatform(_) => Right(UnknownPlatformConfiguration(network))
      }
    }

    /**
      * Reads a specific entry in the configuration file, to create a valid akka-http client host-pool configuration
      *
      * @param namespace the path where the custom configuration will be searched-for
      */
    def loadAkkaStreamingClientConfig(namespace: String): Either[ConfigReaderFailures, HttpStreamingConfiguration] =
      // this is where akka searches for the config entry for host connection pool
      loadConfigForEntryPath(namespace, "akka.http.host-connection-pool").map(HttpStreamingConfiguration)

    private def loadConfigForEntryPath(
        namespace: String,
        referenceEntryPath: String
    ): Either[ConfigReaderFailures, Config] = {
      //read a conseil-specific entry into the expected path for the config
      def loadValidatedConfig(rootConfig: Config): Either[ConfigReaderFailures, Config] =
        Try(
          rootConfig
            .getConfig(namespace)
            .atPath(referenceEntryPath) //puts the config entry where expected by akka
            .withFallback(rootConfig) //adds default values, where not overriden
            .ensuring(
              endConfig =>
                //verifies all expected entries are there
                Try(endConfig.checkValid(rootConfig.getConfig(referenceEntryPath), referenceEntryPath)).isSuccess
            )
        ).toEither.left.map {
          //wraps the error into pureconfig's one
          t =>
            ConfigReaderFailures(new ThrowableFailure(t, None))
        }

      //compose the configuration reading outcomes
      for {
        akkaConf <- pureconfig.loadConfig[Config]
        validatedConfig <- loadValidatedConfig(akkaConf)
      } yield validatedConfig
    }

  }

  object Csv extends LazyLogging {
    import kantan.csv._
    import kantan.codecs.strings.StringDecoder
    import slick.lifted.{AbstractTable, TableQuery}
    import java.sql.Timestamp
    import java.time.Instant
    import java.time.format.DateTimeFormatter
    import shapeless._
    import shapeless.ops.hlist._

    /** Trims if passed a String value, otherwise returns the value unchanged */
    object Trimmer extends Poly1 {
      implicit val stringTrim = at[String] { _.trim }
      implicit def noop[T] = at[T] { identity }
    }

    implicit val timestampDecoder: CellDecoder[java.sql.Timestamp] = (e: String) => {
      val format = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)")
      StringDecoder
        .makeSafe("Instant")(s => Instant.from(format.parse(s)))(e)
        .map(Timestamp.from)
        .left
        .map(x => DecodeError.TypeError(x.message))
    }

    /** Reads a CSV file with tabular data, returning a set of rows to save in the database.
      * This overload will find the source file based on the slick table and the network provided.
      *
      * @tparam T is the slick table for which we want to parse rows objects
      * @tparam H a generic representation used to automatically map the row from values
      * @param table used to find which table rows are requested as output
      * @param network will be used to find the source csv to parse in the search path
      * @param separator defaults to ',', will be used to split the csv tokens
      * @param hd implicitly provide a way to read field types from the csv headers
      * @param g type assertion that a Generic HList can be created from the table row shape
      * @param m type assertion to state the the HList can be used to map the contents of fields,
      *          via the polymorphic [[Trimmer]] function
      * @return the list of parsed rows for the specific table
      */
    def readTableRowsFromCsv[T <: AbstractTable[_], H <: HList](
        table: TableQuery[T],
        network: String,
        separator: Char = ','
    )(
        implicit
        hd: HeaderDecoder[T#TableElementType],
        g: Generic.Aux[T#TableElementType, H],
        m: Mapper.Aux[Trimmer.type, H, H]
    ): Option[List[T#TableElementType]] =
      readRowsFromCsv[T#TableElementType, H](
        csvSource = getClass.getResource(s"/${table.baseTableRow.tableName}/$network.csv"),
        separator
      )

    /** Reads a CSV file with tabular data, returning a set of rows to save in the database
      * This overload will use the provided csv file
      *
      * To call this method you will need to provide the expected type parameters explicitly,
      * otherwise the generic method won't be able to fill out the pieces.
      * To obtain the specific HList type for the [[H]] constraint, we need to pull a "trick",
      * by using shapeless generic facilities:
      *
      * Creating first the [[shapeless.Generic\[Row]]] instance and then accessing its [[Repr]]
      * type attribute, which will be the specific [[HList]]'s recursive subtype needed to
      * represent the specific [[Row]] fields.
      * e.g.
      * {{{
      * import shapeless._
      *
      * val genericRow = Generic[MyRow]
      *
      * val rowList: List[MyRow] = ConfigUtil.Csv.readTableRowsFromCsv[MyRow, genericRow.Repr](csvUrl)
      *
      * }}}
      *
      * @tparam Row is the type of the parsed objects
      * @tparam H a generic representation used to automatically map the row from values
      * @param csvSource points to the csv file to parse
      * @param separator defaults to ',', will be used to split the csv tokens
      * @param hd implicitly provide a way to read field types from the csv headers
      * @param g type assertion that a Generic HList can be created from the table row shape
      * @param m type assertion to state the the HList can be used to map the contents of fields,
      *          via the polymorphic [[Trimmer]] function
      * @return the list of parsed rows
      */
    def readRowsFromCsv[Row, H <: HList](
        csvSource: java.net.URL,
        separator: Char = ','
    )(
        implicit
        hd: HeaderDecoder[Row],
        g: Generic.Aux[Row, H],
        m: Mapper.Aux[Trimmer.type, H, H]
    ): Option[List[Row]] = {
      import kantan.csv._
      import kantan.csv.ops._

      /* Uses a Generic to transform the instance into an HList, maps over it and convert it back into the case class */
      def trimStringFields[C](c: C)(implicit g: Generic.Aux[C, H]): C = {
        val hlist = g.to(c)
        val trimmed = hlist.map(Trimmer)
        g.from(trimmed)
      }

      Option(csvSource).map { validSource =>
        val reader: CsvReader[ReadResult[Row]] =
          validSource.asCsvReader[Row](rfc.withHeader.withCellSeparator(separator))

        // separates List[Either[L, R]] into List[L] and List[R]
        val (errors, rows) = reader.toList.foldRight((List.empty[ReadError], List.empty[Row]))(
          (acc, pair) => acc.fold(l => (l :: pair._1, pair._2), r => (pair._1, trimStringFields(r) :: pair._2))
        )

        if (errors.nonEmpty) {
          val messages = errors.map(_.getMessage).mkString("\n", "\n", "\n")
          logger.error("Error while reading registered source file {}: {}", csvSource.toExternalForm(), messages)
        }

        rows
      }
    }

  }

}
