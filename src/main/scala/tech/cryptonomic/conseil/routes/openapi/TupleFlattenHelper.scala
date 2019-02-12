package tech.cryptonomic.conseil.routes.openapi

trait TupleFlattenHelper {
  import shapeless._
  import ops.tuple.FlatMapper

  trait LowPriorityFlatten extends Poly1 {
    implicit def default[T] = at[T](Tuple1(_))
  }
  object flatten extends LowPriorityFlatten {
    implicit def caseTuple[P <: Product](implicit lfm: Lazy[FlatMapper[P, flatten.type]]) =
      at[P](lfm.value(_))
  }
}
