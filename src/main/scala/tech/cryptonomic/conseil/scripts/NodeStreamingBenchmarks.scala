package tech.cryptonomic.conseil

import akka.actor._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util._
import tech.cryptonomic.conseil.config._
import tech.cryptonomic.conseil.config.Platforms._
import tech.cryptonomic.conseil.tezos.ShutdownComplete
import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig.loadAkkaStreamingClientConfig

package tezos {
  case class CustomNodeInterface(override val streamingRequestsConnectionPooling: ConnectionPoolSettings)(
    implicit system: ActorSystem,
    tezosConf: TezosConfiguration,
    reqConf: NetworkCallsConfiguration,
    clientConf: HttpStreamingConfiguration
  ) extends TezosNodeInterface(tezosConf, reqConf, clientConf)(system)
}

package scripts {

  object NodeStreamingScenarios {

    val customizedPoolingConf = (custom: String) =>
      ConfigFactory
        .parseString(custom)
        .atPath("akka.http.host-connection-pool")
        .withFallback(ConfigFactory.load())

    val initialConfig =
      """ max-connections = 30
        | max-open-requests = 2048
        | idle-timeout = 10 minutes
        | pipelining-limit = 7
      """.stripMargin
  }

  object NodeStreamingBenchmark extends App {
    import pureconfig.{loadConfig, CamelCase, ConfigFieldMapping}
    import pureconfig.generic.auto._
    import pureconfig.generic.ProductHint
    import NodeStreamingScenarios._

    //applies convention to uses CamelCase when reading config fields
    implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

    implicit val system: ActorSystem = ActorSystem("node-interface-bench")
    implicit val dispatcher = system.dispatcher

    val network = "zeronet"

    //load config and run
    val loadedConf = for {
      reqConf <- loadConfig[NetworkCallsConfiguration]("")
      nodeConf <- loadConfig[TezosNodeConfiguration](namespace = s"platform.tezos.$network.node")
      clientConf <- loadAkkaStreamingClientConfig(namespace = "akka.tezos-streaming-client")
    } yield (TezosConfiguration(network, nodeConf), reqConf, clientConf)

    loadedConf.foreach { conf =>
      implicit val tzCfg = conf._1
      implicit val reqCfg = conf._2
      implicit val clientCfg = conf._3

      println(s"POOL CONFIG:\n$initialConfig")
      lazy val initial = ConnectionPoolSettings(customizedPoolingConf(initialConfig))
      val node = tezos.CustomNodeInterface(initial)

      sys.addShutdownHook {
        import scala.concurrent.ExecutionContext.Implicits.global
        println("System closing...")
        val nodeShutdown = node
          .shutdown()
          .flatMap((_: ShutdownComplete) => system.terminate())

        Await.result(nodeShutdown, 1.second)
        println("All engines stopped.")
      }

      val downloadRange = 5 :: 10 :: 20 :: 50 :: 100 :: 1000 :: 2500 :: 5000 :: 10000 :: Nil

      val concurrency = 50
      println(s"concurrency of the streamed result processing: $concurrency")

      downloadRange foreach { offset =>
        println(s"SCENARIO: running with offset range of $offset")
        println("FETCHING BLOCKS")

        val blocksUrls = (0 to offset).map(v => s"blocks/head~$v").toList

        val start = System.nanoTime()
        node.runBatchedGetQuery(network, blocksUrls, identity[String], concurrency).onComplete {
          case Success(jsonData) =>
            val latency = Duration(System.nanoTime() - start, "nanoseconds")
            println(s"FETCHING BLOCKS completed for range: $offset in ${latency.toSeconds}s")
          case Failure(err) =>
            println(s"FETCHING BLOCKS failed for range: $offset with ${err.getMessage}")
        }

      }
    }

    println("Waiting for the benchmark to end... click enter to exit")
    scala.io.StdIn.readLine
  }

}
