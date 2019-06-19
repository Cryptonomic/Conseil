package tech.cryptonomic.conseil.util

import scala.collection.TraversableLike
import cats.instances.option._
import cats.syntax.applicative._
import cats.syntax.apply._

import scala.concurrent.{ExecutionContext, Future}

object CollectionOps {

  /**
    * provides a pretty common grouping opearation for collections of 2-tuples
    * aggregating by the first element of the tuple as a key
    */
  def groupByKey[K, V](pairedSeq: Seq[(K, V)]): Map[K, Seq[V]] =
    pairedSeq.groupBy {
      case (k, v) => k
    }.mapValues { pairs =>
      pairs.map {
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
  def applyOnBounds[T, R, Coll[A] <: TraversableLike[A, _]](list: Coll[T])(function: (T, T) => R): Option[R] =
    function.pure[Option].ap2(list.headOption, list.lastOption)

  /** allows operating on collecition boundaries as an extension method */
  implicit class BoundedAppication[T, R, Coll[A] <: TraversableLike[A, _]](list: Coll[T]) {
    def onBounds(f: (T, T) => R): Option[R] = applyOnBounds(list)(f)
  }

  // simplifies mapping of an embedded structure (Option[List[..]])
  implicit class ExtendedOptionalList[T](val value: Option[List[T]]) extends AnyVal {
    def mapNested(function: T => Option[T]): Option[List[T]] = value.map(_.flatMap(function(_)))
  }

  // simplifies mapping of an embedded structure (Future[List[..]])
  implicit class ExtendedFuture[T](val value: Future[List[T]]) extends AnyVal {
    def mapNested(function: T => Option[T])(implicit apiExecutionContext: ExecutionContext): Future[List[T]] =
      value.map(_.flatMap(function(_)))
  }
}
