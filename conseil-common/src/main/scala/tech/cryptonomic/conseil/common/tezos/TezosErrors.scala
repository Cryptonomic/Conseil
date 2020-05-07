package tech.cryptonomic.conseil.common.tezos

/** Defines high-level errors encountered during processing */
trait TezosErrors {

  /** Something went wrong during handling of Blocks or related sub-data */
  case class BlocksProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException

  /** Something went wrong during handling of Accounts */
  case class AccountsProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException

  /** Something went wrong during handling of Delegates */
  case class BakersProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException

}
