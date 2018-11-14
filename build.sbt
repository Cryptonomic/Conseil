name := "Conseil"
scalaVersion := "2.12.4"

val akkaHttpVersion = "10.1.0"
val akkaVersion = "2.5.11"

ThisBuild / scapegoatVersion := "1.3.8"
scapegoatIgnoredFiles := Seq(".*/tech/cryptonomic/conseil/tezos/Tables.scala")

libraryDependencies  ++=  Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe" % "config" % "1.3.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion exclude("com.typesafe", "config"),
  "com.typesafe.akka" %% "akka-stream" % akkaVersion exclude("com.typesafe", "config"),
  "com.typesafe.akka" %% "akka-actor" % akkaVersion exclude("com.typesafe", "config"),
  "org.scalaj" % "scalaj-http_2.12" % "2.3.0",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.0",
  "com.typesafe.slick" %% "slick" % "3.2.1" exclude("org.reactivestreams", "reactive-streams") exclude("com.typesafe", "config") exclude("org.slf4j", "slf4j-api"),
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1" exclude("org.slf4j", "slf4j-api"),
  "org.postgresql" % "postgresql" % "42.1.4",
  "com.typesafe.slick" %% "slick-codegen" % "3.2.1",
  "org.scalamock" %% "scalamock" % "4.0.0" % Test,
  "com.madgag.spongycastle" % "core" % "1.58.0.0",
  "org.scorexfoundation" %% "scrypto" % "2.0.0",
  "com.muquit.libsodiumjna" % "libsodium-jna" % "1.0.4" exclude("org.slf4j", "slf4j-log4j12") exclude("org.slf4j", "slf4j-api"),
  "com.github.alanverbner" %% "bip39" % "0.1",
  "ch.megard" %% "akka-http-cors" % "0.3.0",
  "com.h2database" % "h2" % "1.4.197" % "test"
)

excludeDependencies ++= Seq(
  "org.consensusresearch" %% "scrypto"
)

assemblyOutputPath in assembly := file("/tmp/conseil.jar")

scalacOptions ++= ScalacOptions.common

//add build information as an object in code
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "tech.cryptonomic.conseil"

//uses git tags to generate the project version
//see https://github.com/sbt/sbt-git#versioning-with-git
enablePlugins(GitVersioning)

/* The versioning scheme is
 *  - use a major number as the platform version
 *  - add a date reference in form of yyww (year + week in year)
 *  - use the latest git tag formatted as "ci-release-<xyz>" and take the numbers from xyz, increasing it
 * Compose the three separated by "dots" to have the version that will be released
 * The Git plugin will add a trailing "-SNAPSHOT" if there are locally uncommitted changes
 */
val majorVersion = 0

/* This allows to extract versions from past tags, not directly applied to
 * the current commit
 */
git.useGitDescribe := true

//defines how to extract the version from git tagging
git.gitTagToVersionNumber := { tag: String =>
  if(Versioning.releasePattern.findAllIn(tag).nonEmpty)
    Some(Versioning.generate(major = majorVersion, date = java.time.LocalDate.now, tag = tag))
  else
    None
}

//custom task to create a new release tag
lazy val prepareReleaseTag = taskKey[String]("Use the current version to define a git-tag for a new release")
prepareReleaseTag := Versioning.prepareReleaseTagDef.value

/* A command to call the "git tag" commands (from sbt-git) with custom args.
 * Allows any automated environment (e.g. jenkins, travis) to call
 * "sbt gitTag" when a new release has been just published, thus bumping the versioning
 * tag and pushing to git
 * In turn, sbt will pick it up for the new version definition
 */
lazy val gitTagCommand = Command.command("gitTag") { state =>
  val extracted = Project.extract(state)
  val (state2, tag) = extracted.runTask(prepareReleaseTag, state)
  //we might want to check out only for non-snapshots?
  println(s"About to tag the new release as '$tag'")
  //we might want to read the message from the env or from a local file
  val command = s"""git tag -a -m "release tagged using sbt gitTag" $tag"""
  Command.process(command, state2)
}

ThisBuild / commands += gitTagCommand

useGpg := true

//sonatype publishing keys
sonatypeProfileName := "tech.cryptonomic"

organization := "tech.cryptonomic"
organizationName := "Cryptomonic"
organizationHomepage := Some(url("https://cryptonomic.tech/"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/Cryptonomic/Conseil"),
    "scm:git@github.com:Cryptonomic/Conseil.git"
  )
)
//should fill this up with whoever needs to appear there
developers := List(
  Developer(
    id    = "ivanopagano",
    name  = "Ivano Pagano",
    email = "ivano.pagano@scalac.io",
    url   = url("https://github.com/ivanopagano")
  )
)

description := "Query API for the Tezos blockchain."
licenses := List("gpl-3.0" -> new URL("https://www.gnu.org/licenses/gpl-3.0.txt"))
homepage := Some(url("https://cryptonomic.tech/"))

// Remove all additional repository other than Maven Central from POM
pomIncludeRepository := { _ => false }
publishMavenStyle := true
publishTo := sonatypePublishTo.value
