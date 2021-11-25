package tech.cryptonomic.conseil.info

import cats.effect.IO

object model {

  final case class Info(application: String, version: String, git: GitInfo)

  val emptyInfo = IO(Info("", "", GitInfo(None, List.empty[String])))

  final case class GitInfo(commitHash: Option[String], tags: List[String])

  final case class GenericServerError(message: String) extends Exception

}
