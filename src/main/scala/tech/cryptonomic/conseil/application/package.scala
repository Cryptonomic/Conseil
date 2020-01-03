package tech.cryptonomic.conseil

package object application {
  import tech.cryptonomic.conseil.tezos.ApiOperations
  import tech.cryptonomic.conseil.tezos.TezosNodeOperator
  import akka.actor.ActorSystem
  import cats.effect.{ContextShift, IO}
  import tech.cryptonomic.conseil.config.LorreAppConfig.CombinedConfiguration
  import tech.cryptonomic.conseil.tezos.FeeOperations
  import slick.jdbc.PostgresProfile.api._

  type CompositeOps = (BlocksOperations, AccountsOperations, DelegatesOperations, FeeOperations, ContextShift[IO])

  /** Collects the resources and builds all the composite operations instances used by Lorre */
  def makeOperations(
      conf: CombinedConfiguration,
      db: Database,
      system: ActorSystem,
      node: TezosNodeOperator,
      api: ApiOperations
  ): CompositeOps = {
    //brings the execution context in scope
    import system.dispatcher

    //to use the actor system thread pool for concurrency, considering IOApp already provides a global one
    implicit val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)

    val blocksOperations = new BlocksOperations(node, db)
    val delegatesOperations =
      new DelegatesOperations(conf.batching.blockPageSize, node, db)
    val accountsOperations =
      new AccountsOperations(delegatesOperations, conf.batching.blockPageSize, node, db, api)

    val feeOperations = new FeeOperations(db)

    (blocksOperations, accountsOperations, delegatesOperations, feeOperations, contextShift)
  }
}
