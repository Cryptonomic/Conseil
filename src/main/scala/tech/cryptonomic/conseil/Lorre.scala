package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging

import cats.data.Kleisli
import cats.syntax.all._
import cats.effect.{ExitCode, IO, IOApp}
import tech.cryptonomic.conseil.config.LorreAppConfig
import tech.cryptonomic.conseil.util.IOUtils.IOLogging
import tech.cryptonomic.conseil.application.{LorreBakerRightsOperations, LorreIndexing}
import cats.Parallel

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends IOApp with LazyLogging with IOLogging with LorreAppConfig {

  //defines the main logic, given all necessary dependencies and configurations
  override def run(args: List[String]): IO[ExitCode] = {
    import LorreAppConfig.ConfiguredResources
    import cats.arrow.Arrow
    import cats.instances.function._

    //builds operations on which Lorre depends, applying the configured resources
    //it uses cats.arrow idioms to pair the input with the output
    val buildDeps: ConfiguredResources => (ConfiguredResources, application.CompositeOps) =
      Arrow[Function].lift(identity[ConfiguredResources]).merge((application.makeOperations _).tupled)

    /* An effectful program built from a function, with "adapted" input
     * The adapter function "extends" the app resources by pairing them with
     * the operational dependencies built upon them.
     */
    val indexingLoop: Kleisli[IO, ConfiguredResources, Unit] = Kleisli(
      (LorreIndexing.run _).tupled
    ).local(buildDeps)

    /* the bakers rights scheduled update */
    val bakerRightsSchedule: Kleisli[IO, ConfiguredResources, Unit] = Kleisli(
      res => new LorreBakerRightsOperations(res).scheduling.void
    )

    /* Combines all the processing functions as a single function,
     * applied to a common input value: i.e. the configured resources.
     * Uses cats.Parallel to pair the kleisli functions and run the io effects in parallel
     * on the available ContextShift[IO]
     */
    val program = Parallel.parProduct(indexingLoop, bakerRightsSchedule).void

    //builds the actual resources and then pass the adapted program as a function to execute
    useConfiguredResources(args)(program.run).as(ExitCode.Success)
  }

}
