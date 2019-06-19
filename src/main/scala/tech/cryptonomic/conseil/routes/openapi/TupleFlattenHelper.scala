package tech.cryptonomic.conseil.routes.openapi

/** Helper object which implements flatten for the tuples using shapeless library
  * Taken from:
  * https://gist.github.com/travisbrown/3945529
  * */
object TupleFlattenHelper {

  import shapeless._
  import shapeless.ops.hlist.{Prepend, Tupler}

  trait Flatten[I, O <: HList] {
    def apply(i: I): O
  }

  trait FlattenLow {
    implicit def otherFlatten[I]: Flatten[I, I :: HNil] = (i: I) => i :: HNil
  }

  object FlattenHigh extends FlattenLow {
    implicit object hnilFlatten extends Flatten[HNil, HNil] {
      def apply(i: HNil): HNil.type = HNil
    }

    implicit def hlistFlatten[H, T <: HList, HO <: HList, TO <: HList, O <: HList](
        implicit
        hev: Flatten[H, HO],
        tev: Flatten[T, TO],
        pre: Prepend.Aux[HO, TO, O]
    ): Flatten[H :: T, O] = (i: H :: T) => pre(hev(i.head), tev(i.tail))

    implicit def tupleFlatten[P <: Product, L <: HList, O <: HList](
        implicit
        lev: Generic.Aux[P, L],
        fev: Flatten[L, O]
    ): Flatten[P, O] = (i: P) => fev(lev.to(i))
  }

  def flatten[In, Out <: HList](in: In)(implicit fev: Flatten[In, Out], tev: Tupler[Out]): tev.Out = tev(fev(in))
}
