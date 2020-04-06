package tech.cryptonomic.conseil.common.util

import scala.annotation.implicitNotFound
import cats.Id

/**
  * A type class that enables to convert an object to another
  * in a single direction (i.e. it's not necessarily an invertible operation).
  * The conversion result is wrapped into a generic "effect" of type `F`
  */
@implicitNotFound(
  "A ${FROM} can't be converted to a ${F} of ${TO}, no `Conversion` instance available in the implicit scope"
)
trait Conversion[F[_], FROM, TO] {

  /** Takes a `FROM` object and retuns the `TO` object, with an effect `F`.*/
  def convert(from: FROM): F[TO]

}

/** type class companion */
object Conversion {

  /** Implicitly summons a `Conversion` instance for the given types,
    * if available in scope.
    */
  def apply[F[_], FROM, TO](implicit conv: Conversion[F, FROM, TO]) = conv

  /** Adds extension methods based on `Conversion.convert` call for any type
    * for which the implicit `Conversion` is available
    */
  object Syntax {

    //extension pattern
    implicit class ConversionOps[FROM](from: FROM) {

      /** converts the object to a `TO` instance, wrapped in a `F` effect. */
      def convertToA[F[_], TO](implicit conv: Conversion[F, FROM, TO]): F[TO] =
        conv.convert(from)

      /** converts the object to a `TO` instance, with no effect wrapping the result */
      def convertTo[TO](implicit conv: Conversion[Id, FROM, TO]): TO =
        conv.convert(from)
    }

  }
}
