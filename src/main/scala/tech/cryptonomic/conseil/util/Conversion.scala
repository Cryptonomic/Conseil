package tech.cryptonomic.conseil.util

trait Conversion[F[_], FROM, TO] {

  def convert(from: FROM): F[TO]

}

object Conversion {

  type Id[T] = T

  def apply[F[_], FROM, TO](implicit conv: Conversion[F, FROM, TO]) = conv

}

object ConversionSyntax {

  implicit class ConversionOps[FROM](from: FROM) {
    def convertToA[F[_], TO](implicit conv: Conversion[F, FROM, TO]): F[TO] =
      conv.convert(from)

    def convertTo[TO](implicit conv: Conversion[Conversion.Id, FROM, TO]): TO =
      conv.convert(from)
  }

}