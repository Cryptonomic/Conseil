package tech.cryptonomic.conseil.common.tezos

object Fork {

  /** This is a conventional value to mark the data considered
    * valid on the main fork of the tezos chain, whatever it currently is.
    * When a fork happens, the default will be substituted with the generated
    * id of the new fork, for all stale data.
    * Any new entry in the database should be marked by default with this value
    * for the `forkId` field, and it will be used to select the main chain data
    * on any regular query.
    */
  final val mainForkId = "leader"

}
