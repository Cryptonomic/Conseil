package tech.cryptonomic.conseil.indexer.config

import scopt.Read

import scala.util.Try

private[config] object ConfigUtil {

  object Depth {
    implicit class DepthOps(val value: String) {
      def toDepth = value match {
        case "-1" | "all" | "everything" => Some(Everything)
        case "0" | "new" | "newest" => Some(Newest)
        case Natural(n) => Some(Custom(n))
        case _ => None
      }
    }

    implicit val depthRead: Read[Option[Depth]] = Read.reads(_.toDepth)
  }

  /** Used to pattern match on natural numbers */
  object Natural {
    def unapply(s: String): Option[Int] = Try(s.toInt).filter(_ > 0).toOption
  }

}
