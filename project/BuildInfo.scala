import com.typesafe.sbt.GitPlugin.autoImport.git
import sbt.Keys._
import sbt.Project
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo.{BuildInfoKey, BuildInfoPlugin}

object BuildInfo {

  implicit class BuildInfoOps(project: Project) {

    //TODO Probably API and Lorre should have BuildInfo structure split across both of these modules
    // Currently name is HardCoded, should be changed, once used by multiple modules
    def enableBuildInfo(): Project =
      project
        .settings(
          buildInfoKeys := Seq(
                BuildInfoKey.of(("name", "Conseil")),
                version,
                scalaVersion,
                sbtVersion,
                git.gitHeadCommit,
                git.gitCurrentTags
              ),
          buildInfoPackage := "tech.cryptonomic.conseil"
        )
        .enablePlugins(BuildInfoPlugin)

  }

}
