package tech.cryptonomic.conseil.common.tezos

import TezosTypes._
import cats._
import cats.implicits._

/** Provide any needed type class instance from cats,
  * for relevant data types in tezos
  *
  * To have them in scope you need to
  * {{{
  *   import tech.cryptonomic.conseil.common.tezos.TezosTypesInstances._
  * }}}
  */
object TezosTypesInstances {

  //defer equality to the underlying equality on Strings
  implicit lazy val tezosBlockHashEq: Eq[TezosBlockHash] =
    (x: TezosBlockHash, y: TezosBlockHash) => x.value.eqv(y.value)

}
