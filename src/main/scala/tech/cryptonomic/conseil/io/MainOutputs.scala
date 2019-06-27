package tech.cryptonomic.conseil.io

import com.typesafe.scalalogging.Logger
import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.config.{LorreConfiguration, ServerConfiguration}
import tech.cryptonomic.conseil.config.Platforms._

/** Defines main output for Lorre or Conseil, at startup */
object MainOutputs {

  /** Defines what to print when starting Conseil */
  trait ConseilOutput {

    /** we need to have a logger */
    protected[this] def logger: Logger

    /** Shows the main application info
      * @param serverConf configuration of the http server
      */
    protected[this] def displayInfo(serverConf: ServerConfiguration) =
      logger.info(
        """
        | ==================================***==================================
        |  Conseil v.{}
        |  {}
        | ==================================***==================================
        |
        | Server started on {} at port {}
        | Bonjour...
        |
        |""".stripMargin,
        BuildInfo.version,
        BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]"),
        serverConf.hostname,
        serverConf.port
      )

    /** Shows details on the current configuration
      * @param platform the platform used by Lorre
      * @param platformConf the details on platform-specific configuratoin (e.g. node connection)
      * @param ignoreFailures env-var name and value read to describe behaviour on common application failure
      * @tparam C the custom platform configuration type (depending on the currently hit blockchain)
      */
    protected[this] def displayConfiguration(platformConfigs: PlatformsConfiguration): Unit =
      logger.info(
        """
        | ==================================***==================================
        | Configuration details
        |
        | {}
        | {}
        |
        | ==================================***==================================
        |
        """.stripMargin,
        showAvailablePlatforms(platformConfigs),
        showDatabaseConfiguration
      )

  }

  /** Defines what to print when starting Lorre */
  trait LorreOutput {

    /** we need to have a logger */
    protected[this] def logger: Logger

    /** Shows the main application info
      * @param platformConf custom configuration for the used chain
      */
    protected[this] def displayInfo(platformConf: PlatformConfiguration) =
      logger.info(
        """
        | ==================================***==================================
        |  Lorre v.{}
        |  {}
        | ==================================***==================================
        |
        |  About to start processing data on the {} network
        |
        |""".stripMargin,
        BuildInfo.version,
        BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]"),
        platformConf.network
      )

    /** Shows details on the current configuration
      * @param platform the platform used by Lorre
      * @param platformConf the details on platform-specific configuratoin (e.g. node connection)
      * @param ignoreFailures env-var name and value read to describe behaviour on common application failure
      * @tparam C the custom platform configuration type (depending on the currently hit blockchain)
      */
    protected[this] def displayConfiguration[C <: PlatformConfiguration](
      platform: BlockchainPlatform,
      platformConf: C,
      lorreConf: LorreConfiguration,
      ignoreFailures: (String, Option[String])
    ): Unit =
      logger.info(
        """
        | ==================================***==================================
        | Configuration details
        |
        | Connecting to {} {}
        | on {}
        |
        | Reference hash for synchronization with the chain: {}
        | Requested depth of synchronization: {}
        | Environment set to skip failed download of chain data: {} [†]
        |
        | {}
        |
        | [†] To let the process proceed on error,
        |     set an environment variable named {} to "true" or "yes"
        | ==================================***==================================
        |
      """.stripMargin,
        platform.name,
        platformConf.network,
        showPlatformConfiguration(platformConf),
        lorreConf.headHash.fold("head")(_.value),
        lorreConf.depth,
        ignoreFailures._2.getOrElse("no"),
        showDatabaseConfiguration,
        ignoreFailures._1
      )

  }

  /* prepare output to display existings platforms and networks */
  private def showAvailablePlatforms(conf: PlatformsConfiguration): String =
    conf.platforms.map {
      case (platform, confs) =>
        val networks = confs.map(_.network).mkString("\n  - ", "\n  - ", "\n")
        s"""
      |  Platform: ${platform.name}$networks
      """.stripMargin
    }.mkString("\n")

  /* prepare output to display database access */
  private val showDatabaseConfiguration: String = {
    import com.typesafe.config._
    import java.util.Map.{Entry => JMEntry}
    import scala.collection.JavaConverters._

    //decides if the value should be hidden, based on key content
    val Secrets = """.*(password|secret).*""".r

    //read java map entries as simple tuples
    implicit def tupleEntry[K, V](entry: JMEntry[K, V]): (K, V) =
      (entry.getKey, entry.getValue)

    //convert config key/value pairs rendering the values as checked text
    def renderValues(entries: (String, ConfigValue)): (String, String) = entries match {
      case ((Secrets(key), value)) =>
        key -> value.render.map(_ => '*')
      case ((key, value)) =>
        key -> value.render
    }

    val dbConf = ConfigFactory.load.getConfig("conseildb").resolve()

    //the meat of the method
    dbConf.entrySet.asScala.map { entry =>
      val (key, value) = renderValues(entry)
      s" - ${key} = ${value}"
    }.mkString("Database configuration:\n\n", "\n", "\n")
  }

  /* custom display of each configuration type */
  private val showPlatformConfiguration: PartialFunction[PlatformConfiguration, String] = {
    case TezosConfiguration(_, TezosNodeConfiguration(host, port, protocol, prefix)) =>
      s"node $protocol://$host:$port/$prefix"
    case _ =>
      "a non-descript platform configuration"
  }

}
