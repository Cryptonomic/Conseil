package tech.cryptonomic.conseil.tezos

/** Defines high-level errors encountered during processing */
trait TezosErrors {

  /** Something went wrong during handling of Accounts */
  case class AccountsProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException

}