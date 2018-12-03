name := "Conseil"
version := "0.0.1"
scalaVersion := "2.12.4"

val akkaHttpVersion = "10.1.0"
val akkaVersion = "2.5.11"

scapegoatVersion in ThisBuild := "1.3.8"
parallelExecution in Test := false
scapegoatIgnoredFiles := Seq(".*/tech/cryptonomic/conseil/tezos/Tables.scala")

libraryDependencies  ++=  Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "org.scalaj" % "scalaj-http_2.12" % "2.3.0",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.0",
  "de.heikoseeberger" %% "akka-http-jackson" % "1.22.0",
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",
  "org.postgresql" % "postgresql" % "42.1.4",
  "com.typesafe.slick" %% "slick-codegen" % "3.2.1",
  "org.scalamock" %% "scalamock" % "4.0.0" % Test,
  "com.madgag.spongycastle" % "core" % "1.58.0.0",
  "org.scorexfoundation" %% "scrypto" % "2.0.0",
  "com.muquit.libsodiumjna" % "libsodium-jna" % "1.0.4" exclude("org.slf4j", "slf4j-log4j12"),
  "com.github.alanverbner" %% "bip39" % "0.1",
  "ch.megard" %% "akka-http-cors" % "0.3.0",
  "com.h2database" % "h2" % "1.4.197" % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)

excludeDependencies ++= Seq(
  "org.consensusresearch" %% "scrypto"
)

assemblyOutputPath in assembly := file("/tmp/conseil.jar")

scalacOptions ++= ScalacOptions.common

import complete.DefaultParsers._

lazy val runConseil = inputKey[Unit]("A conseil run task.")
fork in runConseil := true
javaOptions in runConseil ++= Seq("-Xms512M", "-Xmx4096M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
runConseil := Def.inputTaskDyn {
  val args = spaceDelimited("").parsed
  runInputTask(Runtime, "tech.cryptonomic.conseil.Conseil", args:_*).toTask("")
}.evaluated

lazy val runLorre = inputKey[Unit]("A lorre run task.")
fork in runLorre := true
javaOptions ++= Seq("-Xmx512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled")
runLorre := Def.inputTaskDyn {
  val args = spaceDelimited("").parsed
  runInputTask(Runtime, "tech.cryptonomic.conseil.Lorre", args:_*).toTask("")
}.evaluated

lazy val genSchema = taskKey[Unit]("A schema generating task.")
fullRunTask(genSchema, Runtime, "tech.cryptonomic.conseil.scripts.GenSchema")
