package tech.cryptonomic.conseil

trait LorreErrors {

  case class AccountsProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException

}