import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.AssemblyPlugin

object Assembly {

  implicit class AssemblyOps(project: Project) {

    def enableAssembly(): Project =
      project
        .settings(
          mainClass in assembly := mainClass.value,
          assemblyOutputPath in assembly := file(s"/tmp/${name.value}.jar"),
          test in assembly := {}
        )
        .enablePlugins(AssemblyPlugin)

    def disableAssembly(): Project =
      project.disablePlugins(AssemblyPlugin)

  }

}
