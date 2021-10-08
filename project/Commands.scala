import sbt.Keys._
import sbt._
import sbtcrossproject.CrossProject

object Commands {

  implicit class CommandOps(project: Project) {
    def addRunCommand(description: String, javaExtras: Seq[String] = Seq.empty, forkOnRun: Boolean = false): Project = {
      import complete.DefaultParsers._

      lazy val runTask = inputKey[Unit](description)

      fork in runTask := forkOnRun
      javaOptions in runTask ++= javaExtras

      project.settings(runTask := Def.inputTaskDyn {
            val args = spaceDelimited("").parsed
            val mainClassValue = mainClass.value match {
              case Some(value) => value
              case None =>
                throw new RuntimeException("Could not initialize command for module without 'mainClass' property!")
            }

            runInputTask(Runtime, mainClassValue, args: _*).toTask("")
          }.evaluated)
    }
  }

  // implicit class CommandOps2(project: CrossProject) {
  //   def addRunCommand(description: String, javaExtras: Seq[String] = Seq.empty, forkOnRun: Boolean = false): CrossProject = {
  //     import complete.DefaultParsers._

  //     lazy val runTask = inputKey[Unit](description)

  //     fork in runTask := forkOnRun
  //     javaOptions in runTask ++= javaExtras

  //     project.settings(runTask := Def.inputTaskDyn {
  //           val args = spaceDelimited("").parsed
  //           val mainClassValue = mainClass.value match {
  //             case Some(value) => value
  //             case None =>
  //               throw new RuntimeException("Could not initialize command for module without 'mainClass' property!")
  //           }

  //           runInputTask(Runtime, mainClassValue, args: _*).toTask("")
  //         }.evaluated)
  //   }
  // }

}
