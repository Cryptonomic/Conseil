import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.{AssemblyPlugin, MergeStrategy}
import sbtassembly.Assembly.isConfigFile
import sbtassembly.PathList

object Assembly {

  implicit class AssemblyOps(project: Project) {

    def enableAssembly(): Project =
      project
        .settings(
          mainClass in assembly := mainClass.value,
          assemblyOutputPath in assembly := file(s"/tmp/${name.value}.jar"),
          test in assembly := {},
          assemblyMergeStrategy in assembly := {
              case "application.conf" | "moduload.list"  => MergeStrategy.concat
	            case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
	            case x if x.endsWith("/module-info.class") => MergeStrategy.discard
              case x => (assemblyMergeStrategy in assembly).value(x)
            }
        )
        .enablePlugins(AssemblyPlugin)

    def disableAssembly(): Project =
      project.disablePlugins(AssemblyPlugin)

  }

}



