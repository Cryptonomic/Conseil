package tech.cryptonomic.conseil.common.io

import tech.cryptonomic.conseil.common.config.Platforms._

/** Defines main output for Lorre or Conseil, at startup */
object MainOutputs {

  /* prepare output to display database access */
  def showDatabaseConfiguration(applicationScope: String): String = {
    import java.util.Map.{Entry => JMEntry}

    import com.typesafe.config._

    import scala.collection.JavaConverters._

    //decides if the value should be hidden, based on key content
    val Secrets = """.*(password|secret).*""".r

    //read java map entries as simple tuples
    implicit def tupleEntry[K, V](entry: JMEntry[K, V]): (K, V) =
      (entry.getKey, entry.getValue)

    //convert config key/value pairs rendering the values as checked text
    def renderValues(entries: (String, ConfigValue)): (String, String) = entries match {
      case (Secrets(key), value) =>
        key -> value.render.map(_ => '*')
      case (key, value) =>
        key -> value.render
    }

    val dbConf = ConfigFactory.load.getConfig(s"$applicationScope.db").resolve()

    //the meat of the method
    dbConf.entrySet.asScala.map { entry =>
      val (key, value) = renderValues(entry)
      s" - $key = $value"
    }.mkString("Database configuration:\n\n", "\n", "\n")
  }

  /* custom display of each configuration type */
  val showPlatformConfiguration: PartialFunction[PlatformConfiguration, String] = {
    case TezosConfiguration(_, _, TezosNodeConfiguration(host, port, protocol, prefix, chainEnv, trace), _, _, _) =>
      s"node $protocol://$host:$port/$prefix/$chainEnv" + (if (trace) " [call tracing enabled]" else "")
    case EthereumConfiguration(network, _, node, _, _, _) =>
      s"network: ${network} node: ${node}"
    case BitcoinConfiguration(_, _, node, _, _) =>
      s"node ${node.url}"
  }

}
