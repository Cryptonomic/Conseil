package tech.cryptonomic.conseil.indexer

import tech.cryptonomic.conseil.common.config.Platforms.BlockchainPlatform
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete

import scala.concurrent.Future

object LorreIndexer {
  sealed trait ShutdownComplete

  /** Used to indicate that shutdown has been completed */
  object ShutdownComplete extends ShutdownComplete
}

/*** Represents the common trait for all of the indexers */
trait LorreIndexer {

  /*** The type of the blockchain platform e.g. Tezos */
  def platform: BlockchainPlatform

  /*** Method, which is executed at the startup, to run main loop */
  def start(): Unit

  /*** Method, which is executed at teardown, to close up all of the resources */
  def stop(): Future[ShutdownComplete]
}
