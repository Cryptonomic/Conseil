import sbtbuildinfo.BuildInfoKey._

name := "Conseil"

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / parallelExecution in Test := false

ThisBuild / scapegoatVersion := "1.3.8"
ThisBuild / scapegoatIgnoredFiles := Seq(".*/tech/cryptonomic/conseil/common/tezos/Tables.scala")

ThisBuild / scalacOptions ++= ScalacOptions.common

lazy val conseil = (project in file("."))
  .aggregate(common, api, lorre)

lazy val common = (project in file("conseil-common"))
  .settings(name := "conseil-common")
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    //TODO Probably API and Lorre should be BuildInfo structure split across both of these modules
    // Currently name is HardCoded
    buildInfoKeys := Seq[BuildInfoKey](
          BuildInfoKey.of(("name", "Conseil")),
          version,
          scalaVersion,
          sbtVersion,
          git.gitHeadCommit,
          git.gitCurrentTags
        ),
    buildInfoPackage := "tech.cryptonomic.conseil"
  )
  .settings(
    scalacOptions += "-P:silencer:pathFilters=common/src/main/scala/tech/cryptonomic/conseil/common/tezos/Tables.scala"
  )
  .enablePlugins(BuildInfoPlugin)

lazy val api = (project in file("conseil-api"))
  .settings(name := "conseil-api")
  .settings(Defaults.itSettings)
  .configs(IntegrationTest)
  .dependsOn(common % "compile->test")

lazy val lorre = (project in file("conseil-lorre"))
  .settings(name := "conseil-lorre")
  .settings(Defaults.itSettings)
  .configs(IntegrationTest)
  .dependsOn(common)

//TODO Split these dependencies better, once the first pull request with splitting will be merged
val akkaVersion = "2.5.21"
val akkaHttpVersion = "10.1.8"
val akkaHttpJsonVersion = "1.25.2"
val slickVersion = "3.3.0"
val catsVersion = "1.6.0"
val monocleVersion = "1.5.1-cats"
val endpointsVersion = "0.9.0"
val circeVersion = "0.11.1"
val http4sVersion = "0.20.10"
val silencerVersion = "1.4.4"
val kantanCsvVersion = "0.6.0"

common / resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

common / libraryDependencies ++= Seq(
  "ch.qos.logback"               % "logback-classic"                % "1.2.3",
  "net.logstash.logback"         % "logstash-logback-encoder"       % "5.3",
  "com.typesafe"                 % "config"                         % "1.3.3",
  "com.typesafe.scala-logging"   %% "scala-logging"                 % "3.7.2",
  "com.typesafe.akka"            %% "akka-actor"                    % akkaVersion exclude ("com.typesafe", "config"),
  "com.typesafe.akka"            %% "akka-stream"                   % akkaVersion exclude ("com.typesafe", "config"),
  "com.typesafe.akka"            %% "akka-http"                     % akkaHttpVersion exclude ("com.typesafe", "config"),
  "com.typesafe.akka"            %% "akka-http-caching"             % akkaHttpVersion exclude ("com.typesafe", "config"),
  "de.heikoseeberger"            %% "akka-http-circe"               % akkaHttpJsonVersion exclude ("com.typesafe.akka", "akka-http"),
  "de.heikoseeberger"            %% "akka-http-jackson"             % akkaHttpJsonVersion exclude ("com.fasterxml.jackson.core", "jackson-databind") exclude ("com.typesafe.akka", "akka-http"),
  "ch.megard"                    %% "akka-http-cors"                % "0.3.4" exclude ("com.typesafe.akka", "akka-http"),
  "org.scalaj"                   %% "scalaj-http"                   % "2.4.1",
  "com.github.pureconfig"        %% "pureconfig"                    % "0.10.2",
  "org.apache.commons"           % "commons-text"                   % "1.7",
  "com.fasterxml.jackson.core"   % "jackson-databind"               % "2.9.6" exclude ("com.fasterxml.jackson.core", "jackson-annotations"),
  "com.fasterxml.jackson.module" %% "jackson-module-scala"          % "2.9.6",
  "com.chuusai"                  %% "shapeless"                     % "2.3.3",
  "org.typelevel"                %% "cats-core"                     % catsVersion,
  "org.typelevel"                %% "mouse"                         % "0.20",
  "com.github.julien-truffaut"   %% "monocle-core"                  % monocleVersion exclude ("org.typelevel.cats", "cats-core"),
  "com.github.julien-truffaut"   %% "monocle-macro"                 % monocleVersion exclude ("org.typelevel.cats", "cats-core") exclude ("org.typelevel.cats", "cats-macros"),
  "org.julienrf"                 %% "endpoints-algebra"             % endpointsVersion,
  "org.julienrf"                 %% "endpoints-openapi"             % endpointsVersion,
  "org.julienrf"                 %% "endpoints-json-schema-generic" % endpointsVersion,
  "org.julienrf"                 %% "endpoints-json-schema-circe"   % endpointsVersion,
  "org.julienrf"                 %% "endpoints-akka-http-server"    % endpointsVersion,
  "org.postgresql"               % "postgresql"                     % "42.1.4",
  "io.circe"                     %% "circe-core"                    % circeVersion,
  "io.circe"                     %% "circe-parser"                  % circeVersion,
  "io.circe"                     %% "circe-generic"                 % circeVersion,
  "io.circe"                     %% "circe-generic-extras"          % circeVersion,
  "com.typesafe.slick"           %% "slick"                         % slickVersion exclude ("org.reactivestreams", "reactive-streams") exclude ("com.typesafe", "config") exclude ("org.slf4j", "slf4j-api"),
  "com.typesafe.slick"           %% "slick-hikaricp"                % slickVersion exclude ("org.slf4j", "slf4j-api"),
  "com.typesafe.slick"           %% "slick-codegen"                 % slickVersion,
  "com.github.tminglei"          %% "slick-pg"                      % "0.18.0",
  "com.kubukoz"                  %% "slick-effect"                  % "0.1.0" exclude ("com.typesafe.slick", "slick"),
  "org.postgresql"               % "postgresql"                     % "42.1.4",
  "com.github.scopt"             %% "scopt"                         % "4.0.0-RC2",
  "io.scalaland"                 %% "chimney"                       % "0.3.1",
  "com.madgag.spongycastle"      % "core"                           % "1.58.0.0",
  "org.scorexfoundation"         %% "scrypto"                       % "2.1.7",
  "org.scorexfoundation"         %% "scorex-util"                   % "0.1.6",
  "com.muquit.libsodiumjna"      % "libsodium-jna"                  % "1.0.4" exclude ("org.slf4j", "slf4j-log4j12") exclude ("org.slf4j", "slf4j-api"),
  "net.java.dev.jna"             % "jna"                            % "5.5.0", //see https://github.com/muquit/libsodium-jna/#update-your-projects-pomxml
  "com.github.alanverbner"       %% "bip39"                         % "0.1",
  "com.rklaehn"                  %% "radixtree"                     % "0.5.1",
  "fr.acinq"                     %% "bitcoin-lib"                   % "0.9.18-SNAPSHOT",
  "com.nrinaudo"                 %% "kantan.csv-generic"            % kantanCsvVersion,
  "com.nrinaudo"                 %% "kantan.csv-java8"              % kantanCsvVersion,
  "com.typesafe.akka"            %% "akka-testkit"                  % akkaVersion % Test exclude ("com.typesafe", "config"),
  "com.typesafe.akka"            %% "akka-http-testkit"             % akkaHttpVersion % Test exclude ("com.typesafe", "config"),
  "org.scalatest"                %% "scalatest"                     % "3.0.5" % "it, test",
  "com.stephenn"                 %% "scalatest-json-jsonassert"     % "0.0.3" % "it, test",
  "org.scalamock"                %% "scalamock"                     % "4.1.0" % "it, test",
  "org.testcontainers"           % "postgresql"                     % "1.12.3" % "it, test",
  "com.softwaremill.diffx"       %% "diffx-scalatest"               % "0.3.3" % "it, test",
  "org.http4s"                   %% "http4s-blaze-client"           % http4sVersion % IntegrationTest,
  "org.http4s"                   %% "http4s-dsl"                    % http4sVersion % IntegrationTest,
  "org.http4s"                   %% "http4s-circe"                  % http4sVersion % IntegrationTest,
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
)

api / libraryDependencies ++= Seq(
  "org.scalatest"     %% "scalatest"         % "3.0.5"         % "it, test",
  "com.typesafe.akka" %% "akka-testkit"      % akkaVersion     % Test exclude ("com.typesafe", "config"),
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test exclude ("com.typesafe", "config"),
  "org.scalamock"     %% "scalamock"         % "4.1.0"         % "it, test"
)

lorre / libraryDependencies ++= Seq(
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
)

common / excludeDependencies ++= Seq(
  "org.consensusresearch" %% "scrypto"
)

assemblyOutputPath in assembly := file("/tmp/conseil.jar")

import complete.DefaultParsers._

lazy val runConseil = inputKey[Unit]("A conseil run task.")
fork in runConseil := true
javaOptions in runConseil ++= Seq("-Xms1024M", "-Xmx8192M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
runConseil := Def.inputTaskDyn {
  val args = spaceDelimited("").parsed
  runInputTask(Runtime, "tech.cryptonomic.conseil.api.Conseil", args: _*).toTask("")
}.evaluated

lazy val runLorre = inputKey[Unit]("A lorre run task.")
fork in runLorre := true
javaOptions ++= Seq("-Xmx512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
runLorre := Def.inputTaskDyn {
  val args = spaceDelimited("").parsed
  runInputTask(Runtime, "tech.cryptonomic.conseil.lorre.Lorre", args: _*).toTask("")
}.evaluated

lazy val genSchema = taskKey[Unit]("A schema generating task.")
fullRunTask(genSchema, Runtime, "tech.cryptonomic.conseil.lorre.scripts.GenSchema")
