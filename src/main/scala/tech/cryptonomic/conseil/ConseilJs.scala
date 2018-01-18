package tech.cryptonomic.conseil

import tech.cryptonomic.conseil.tezos.ApiOperations

import scala.util.Try

object ConseilJs extends App {

  def getAllBlocks = Try(ApiOperations.fetchBlocks)

}
