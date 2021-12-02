package tech.cryptonomic.conseil.indexer.config

import com.github.ghik.silencer.silent
import pureconfig.generic.EnumCoproductHint
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

    /* used by scopt to parse the depth object */
    @silent("private val depthRead in object ConfigUtil is never used")
    implicit val depthRead: Read[Option[Depth]] = Read.reads(_.toDepth)
    @silent("local val depthHint in object ConfigUtil is never used")
    implicit val depthHint: EnumCoproductHint[Depth] = new EnumCoproductHint[Depth]
  }

  /** Used to pattern match on natural numbers */
  object Natural {
    def unapply(s: String): Option[Int] = Try(s.toInt).filter(_ > 0).toOption
  }

}
