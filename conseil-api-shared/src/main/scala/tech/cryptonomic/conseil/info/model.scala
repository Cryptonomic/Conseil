package tech.cryptonomic.conseil.info

import cats.effect.IO

import tech.cryptonomic.conseil.BuildInfo

object model {

  final case class Info(application: String, version: String, git: GitInfo)

  val currentInfo =
    IO(Info(BuildInfo.name, BuildInfo.version, GitInfo(BuildInfo.gitHeadCommit, BuildInfo.gitCurrentTags.toList)))

  final case class GitInfo(commitHash: Option[String], tags: List[String])

  final case class GenericServerError(message: String) extends Exception

}
