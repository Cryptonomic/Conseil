package tech.cryptonomic.conseil.util

import scala.collection.TraversableLike
import cats.instances.option._
import cats.syntax.applicative._
import cats.syntax.apply._

object CollectionOps {

  /**
    * provides common group opearaion for collections of 2-tuples
    * aggregating by the first element of the tuple as a key
    */
  def groupByKey[K, V](pairedSeq: Seq[(K, V)]): Map[K, Seq[V]] =
    pairedSeq.groupBy {
      case (k, v) => k
    }.mapValues {
      pairs => pairs.map {
        case (k, vs) => vs
      }
    }

  /** allows grouping by key as an extension method */
  implicit class KeyedSeq[K, V](seq: Seq[(K, V)]) {
    def byKey(): Map[K, Seq[V]] = groupByKey(seq)
  }

  /**
    * allows to apply a function on collection boundaries, if they're available
    */
  def applyOnBounds[T, R, Coll[A] <: TraversableLike[A, _]](list: Coll[T])(function: (T, T) => R): Option[R] = {
    function.pure[Option].ap2(list.headOption, list.lastOption)
  }

  /** allows operating on collecition boundaries as an extension method */
  implicit class BoundedAppication[T, R, Coll[A] <: TraversableLike[A, _]](list: Coll[T]) {
    def onBounds(f: (T, T) => R): Option[R] = applyOnBounds(list)(f)
  }


}
