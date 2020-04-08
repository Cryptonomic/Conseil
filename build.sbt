import Assembly._
import BuildInfo._
import Commands._

name := "Conseil"

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / parallelExecution in Test := false

ThisBuild / scapegoatVersion := "1.3.8"
ThisBuild / scapegoatIgnoredFiles := Seq(".*/tech/cryptonomic/conseil/common/tezos/Tables.scala")

ThisBuild / scalacOptions ++= ScalacOptions.common

ThisBuild / resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

lazy val conseil = (project in file("."))
  .disableAssembly()
  .aggregate(common, api, lorre)

lazy val common = (project in file("conseil-common"))
  .settings(
    name := "conseil-common",
    libraryDependencies ++= Dependencies.conseilCommonInclude,
    excludeDependencies ++= Dependencies.conseilCommonExclude,
    coverageExcludedPackages := Seq(
      "tech.cryptonomic.conseil.common.io.*",
      "tech.cryptonomic.conseil.common.tezos.Tables",
      "tech.cryptonomic.conseil.common.config.Security",
    ).mkString(";")
  )
  .settings(Defaults.itSettings)
  .settings(
    scalacOptions += "-P:silencer:pathFilters=common/src/main/scala/tech/cryptonomic/conseil/common/tezos/Tables.scala"
  )
  .configs(IntegrationTest)
  .enableBuildInfo()
  .disableAssembly()

lazy val api = (project in file("conseil-api"))
  .settings(
    name := "conseil-api",
    mainClass := Some("tech.cryptonomic.conseil.api.Conseil"),
    libraryDependencies ++= Dependencies.conseilApiInclude,
    coverageExcludedPackages := Seq(
      "<empty>",
      ".*\\.Conseil",
      ".*\\.ConseilAppConfig",
    ).mkString(";")
  )
  .settings(Defaults.itSettings)
  .configs(IntegrationTest)
  .addRunCommand(
    description = "A run application Task.",
    javaExtras = Seq("-Xms1024M", "-Xmx8192M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
  )
  .enableAssembly()
  .dependsOn(common % "compile->test")

lazy val lorre = (project in file("conseil-lorre"))
  .settings(
    name := "conseil-lorre",
    mainClass := Some("tech.cryptonomic.conseil.lorre.Lorre"),
    libraryDependencies ++= Dependencies.conseilLorreInclude,
    coverageExcludedPackages := Seq(
      "<empty>",
      ".*\\.Lorre",
      ".*\\.LorreAppConfig",
    ).mkString(";")
  )
  .settings(Defaults.itSettings)
  .configs(IntegrationTest)
  .addRunCommand(
    description = "A run application Task.",
    javaExtras = Seq("-Xmx512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
  )
  .enableAssembly()
  .dependsOn(common)

lazy val lorreSchema = (project in file("conseil-lorre-schema"))
  .settings(
    name := "conseil-lorre-schema",
    mainClass := Some("tech.cryptonomic.conseil.lorre.schema.GenSchema")
  )
  .addRunCommand(description = "A run schema generating Task.")
  .enableAssembly()
  .dependsOn(common)

addCommandAlias("runApi", "; api/runTask")
addCommandAlias("runLorre", "; lorre/runTask")
addCommandAlias("runLorreSchema", "; lorreSchema/runTask")
