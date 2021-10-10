import Assembly._
import BuildInfo._
import Commands._

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbtcrossproject.CrossProject
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

name := "Conseil"

// Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / parallelExecution in Test := false

ThisBuild / scapegoatVersion := "1.3.8"
// ThisBuild / scapegoatVersion := "1.3.11"
ThisBuild / scapegoatIgnoredFiles := Seq(".*/tech/cryptonomic/conseil/common/tezos/Tables.scala")

ThisBuild / scalacOptions ++= ScalacOptions.common

ThisBuild / resolvers ++= Seq(Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots"))

lazy val conseil = (project in file("."))
  .aggregate(common.jvm, commonTestKit.jvm, api.jvm, client, lorre, schema, smokeTests)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("conseil-common"))
  .settings(
    name := "conseil-common",
    libraryDependencies ++= Dependencies.conseilCommonInclude,
    coverageExcludedPackages := Seq(
          "<empty>",
          "tech.cryptonomic.conseil.common.io.*",
          "tech.cryptonomic.conseil.common.bitcoin.Tables",
          "tech.cryptonomic.conseil.common.ethereum.Tables",
          "tech.cryptonomic.conseil.common.tezos.Tables",
          "tech.cryptonomic.conseil.common.tezos.TezosDataGeneration"
        ).mkString(";")
  )
  .settings(
    scalacOptions += "-P:silencer:pathFilters=common/src/main/scala/tech/cryptonomic/conseil/common/tezos/Tables.scala",
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
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(commonTestKit % Test)

lazy val commonTestKit = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("conseil-common-testkit"))
  .settings(
    name := "conseil-common-testkit",
    libraryDependencies ++= Dependencies.conseilCommonTestKitInclude
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

lazy val api: CrossProject = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("conseil-api"))
  .settings(
    name := "conseil-api",
    mainClass := Some("tech.cryptonomic.conseil.api.Conseil"),
    libraryDependencies ++= (Dependencies.conseilApiInclude ++ Seq(
              "org.endpoints4s" %%% "algebra"             % "1.5.0",
              "org.endpoints4s" %%% "openapi"             % "3.1.0",
              "org.endpoints4s" %%% "json-schema-generic" % "1.5.0",
              "org.endpoints4s" %%% "json-schema-circe"   % "1.5.0",
              "org.endpoints4s" %% "akka-http-server"     % "5.1.0"
            )),
    coverageExcludedPackages := Seq(
          "<empty>",
          "tech.cryptonomic.conseil.api.Conseil",
          "tech.cryptonomic.conseil.api.ConseilApi",
          "tech.cryptonomic.conseil.api.ConseilMainOutput",
          "tech.cryptonomic.conseil.api.config.ConseilAppConfig",
          "tech.cryptonomic.conseil.api.security.Security",
          "tech.cryptonomic.conseil.api.routes.platform.TezosApi"
        ).mkString(";")
  )
  // .addRunCommand(
  //   description = "Task to run the main Conseil API Server",
  //   javaExtras = Seq("-Xms1024M", "-Xmx8192M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
  // )
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(common, commonTestKit % Test)

lazy val client = (project in file("conseil-client"))
  .settings(
    libraryDependencies += "org.endpoints4s" %%% "xhr-client" % "3.1.0",
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
  .enablePlugins(BuildInfoPlugin, ScalaJSPlugin)
  .dependsOn(api.jvm)

lazy val lorre = (project in file("conseil-lorre"))
  .settings(
    name := "conseil-lorre",
    mainClass := Some("tech.cryptonomic.conseil.indexer.Lorre"),
    libraryDependencies ++= Dependencies.conseilLorreInclude,
    coverageExcludedPackages := Seq(
          "<empty>",
          "tech.cryptonomic.conseil.indexer.Lorre",
          "tech.cryptonomic.conseil.indexer.config.LorreAppConfig",
          "tech.cryptonomic.conseil.indexer.logging.LorreInfoLogging",
          "tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging",
          "tech.cryptonomic.conseil.indexer.tezos.TezosIndexer"
        ).mkString(";")
  )
  .addRunCommand(
    description = "Task to run the main Lorre indexing process for Tezos",
    javaExtras = Seq("-Xmx512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
  )
  .enableAssembly()
  .dependsOn(common.jvm, commonTestKit.jvm % Test)

lazy val schema = (project in file("conseil-schema"))
  .settings(
    name := "conseil-schema",
    mainClass := Some("tech.cryptonomic.conseil.schema.GenSchema"),
    libraryDependencies ++= Dependencies.conseilSchemaInclude
  )
  .addRunCommand(description = "Task to generate the schema source files from db-schema")
  .disableAssembly()
  .disablePlugins(ScoverageSbtPlugin)
  .dependsOn(common.jvm)

lazy val smokeTests = (project in file("conseil-smoke-tests"))
  .settings(
    name := "conseil-smoke-tests",
    mainClass := Some("tech.cryptonomic.conseil.smoke.tests.RegressionRun"),
    libraryDependencies ++= Dependencies.conseilSmokeTestsInclude,
    coverageExcludedPackages := Seq("<empty>", "tech.cryptonomic.conseil.smoke.tests.*").mkString(";")
  )
  .addRunCommand(description = "Task to run smoke tests locally")
  .disableAssembly()

addCommandAlias("runApi", "; api/runTask")
addCommandAlias("runLorre", "; lorre/runTask")
addCommandAlias("runSchema", "; schema/runTask")
addCommandAlias("runSmokeTests", "; smokeTests/runTask")
