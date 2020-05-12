import sbt._

object Dependencies {

  private val config = Seq("com.typesafe" % "config" % "1.3.3")

  private val pureConfig = Seq("com.github.pureconfig" %% "pureconfig" % "0.10.2")

  private val scopt = Seq("com.github.scopt" %% "scopt" % "4.0.0-RC2")

  private val logging = Seq(
    "ch.qos.logback"             % "logback-classic"          % "1.2.3",
    "net.logstash.logback"       % "logstash-logback-encoder" % "5.3",
    "com.typesafe.scala-logging" %% "scala-logging"           % "3.7.2"
  )

  private val akkaVersion = "2.5.21"
  private val akka = Seq(
    "com.typesafe.akka" %% "akka-actor"   % akkaVersion exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-stream"  % akkaVersion exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test exclude ("com.typesafe", "config")
  )

  private val akkaHttpVersion = "10.1.8"
  private val akkaHttp = Seq(
    "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-http-caching" % akkaHttpVersion exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test exclude ("com.typesafe", "config")
  )

  private val akkaHttpJsonVersion = "1.26.0"
  private val akkaHttpJson = Seq(
    "de.heikoseeberger" %% "akka-http-circe"   % akkaHttpJsonVersion exclude ("com.typesafe.akka", "akka-http"),
    "de.heikoseeberger" %% "akka-http-jackson" % akkaHttpJsonVersion exclude ("com.fasterxml.jackson.core", "jackson-databind") exclude ("com.typesafe.akka", "akka-http")
  )

  private val akkaHttpCors = Seq("ch.megard" %% "akka-http-cors" % "0.3.4" exclude ("com.typesafe.akka", "akka-http"))

  private val slickVersion = "3.3.0"
  private val slick = Seq(
    "com.typesafe.slick" %% "slick"          % slickVersion exclude ("org.reactivestreams", "reactive-streams") exclude ("com.typesafe", "config") exclude ("org.slf4j", "slf4j-api"),
    "com.typesafe.slick" %% "slick-hikaricp" % slickVersion exclude ("org.slf4j", "slf4j-api")
  )
  private val slickCodeGen = Seq("com.typesafe.slick" %% "slick-codegen" % slickVersion)
  private val slickPG = Seq("com.github.tminglei"     %% "slick-pg"      % "0.18.0")
  private val slickEffect = Seq("com.kubukoz"         %% "slick-effect"  % "0.1.0" exclude ("com.typesafe.slick", "slick"))

  private val postgres = Seq("org.postgresql" % "postgresql" % "42.1.4")

  private val endpointsVersion = "0.9.0"
  private val endpoints = Seq(
    "org.julienrf" %% "endpoints-algebra"             % endpointsVersion,
    "org.julienrf" %% "endpoints-openapi"             % endpointsVersion,
    "org.julienrf" %% "endpoints-json-schema-generic" % endpointsVersion,
    "org.julienrf" %% "endpoints-json-schema-circe"   % endpointsVersion,
    "org.julienrf" %% "endpoints-akka-http-server"    % endpointsVersion
  )

  private val cats = Seq("org.typelevel"  %% "cats-core" % "1.6.0")
  private val mouse = Seq("org.typelevel" %% "mouse"     % "0.20") // related to cats

  private val monocleVersion = "1.5.1-cats"
  private val monocle = Seq(
    "com.github.julien-truffaut" %% "monocle-core"  % monocleVersion exclude ("org.typelevel.cats", "cats-core"),
    "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion exclude ("org.typelevel.cats", "cats-core") exclude ("org.typelevel.cats", "cats-macros")
  )

  private val circeVersion = "0.11.1"
  private val circe = Seq(
    "io.circe" %% "circe-core"           % circeVersion,
    "io.circe" %% "circe-parser"         % circeVersion,
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion
  )

  private val http4sVersion = "0.20.10"
  private val http4s = Seq(
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )

  private val silencerVersion = "1.4.4"
  private val silencer = Seq(
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
  )

  private val kantanCsvVersion = "0.6.0"
  private val kantanCsv = Seq(
    "com.nrinaudo" %% "kantan.csv-generic" % kantanCsvVersion,
    "com.nrinaudo" %% "kantan.csv-java8"   % kantanCsvVersion
  )

  private val jacksonVersion = "2.9.6"
  private val jackson = Seq(
    "com.fasterxml.jackson.core"   % "jackson-databind"      % jacksonVersion exclude ("com.fasterxml.jackson.core", "jackson-annotations"),
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion
  )

  private val scalaTestCompile = Seq("org.scalatest" %% "scalatest" % "3.0.5") // Dedicated for common-testkit
  private val scalaTest = scalaTestCompile.map(_     % Test)
  private val scalaTestJson = Seq("com.stephenn"     %% "scalatest-json-jsonassert" % "0.0.3" % Test)

  private val scalaMock = Seq("org.scalamock" %% "scalamock" % "4.1.0" % Test)

  private val postgresTestContainerCompile = Seq("org.testcontainers"    % "postgresql" % "1.12.3")
  private val postgresTestContainer = postgresTestContainerCompile.map(_ % Test)

  private val diffX = Seq("com.softwaremill.diffx" %% "diffx-scalatest" % "0.3.3" % Test)

  private val apacheCommonsText = Seq("org.apache.commons" % "commons-text" % "1.7")

  private val radixTree = Seq("com.rklaehn" %% "radixtree" % "0.5.1")

  private val jna = Seq(
    "com.muquit.libsodiumjna" % "libsodium-jna" % "1.0.4" exclude ("org.slf4j", "slf4j-log4j12") exclude ("org.slf4j", "slf4j-api"),
    "net.java.dev.jna"        % "jna"           % "5.5.0" //see https://github.com/muquit/libsodium-jna/#update-your-projects-pomxml
  )

  private val chimney = Seq("io.scalaland" %% "chimney" % "0.3.1")

  private val bitcoin = Seq("fr.acinq" %% "bitcoin-lib" % "0.9.18-SNAPSHOT")

  private val scorex = Seq(
    "org.scorexfoundation" %% "scrypto"     % "2.1.7",
    "org.scorexfoundation" %% "scorex-util" % "0.1.6"
  )

  //List of dependencies that are not really used (?) - to be removed
  ////      "com.chuusai"             %% "shapeless"   % "2.3.3",
  ////      "com.madgag.spongycastle" % "core"         % "1.58.0.0",
  ////      "com.github.alanverbner"  %% "bip39"       % "0.1",
  ////      "org.scalaj"              %% "scalaj-http" % "2.4.1",
  val conseilCommonInclude: Seq[ModuleID] =
    concat(
      logging,
      slick,
      slickCodeGen,
      slickPG,
      slickEffect,
      postgres,
      circe,
      cats,
      mouse,
      radixTree,
      jna,
      chimney,
      silencer,
      monocle,
      kantanCsv,
      jackson,
      scalaTest,
      scalaTestJson,
      scalaMock,
      postgresTestContainer,
      diffX,
      apacheCommonsText,
      bitcoin,
      scorex
    )

  val conseilCommonTestKitInclude: Seq[ModuleID] = concat(config, slick, scalaTestCompile, postgresTestContainerCompile)

  val conseilApiInclude: Seq[ModuleID] =
    concat(
      config,
      logging,
      pureConfig,
      scopt,
      akka,
      akkaHttp,
      akkaHttpJson,
      akkaHttpCors,
      silencer,
      scalaMock,
      diffX,
      endpoints
    )

  val conseilLorreInclude: Seq[ModuleID] =
    concat(config, logging, pureConfig, scopt, silencer, akka, akkaHttp, scalaTest, scalaMock)

  val conseilSchemaInclude: Seq[ModuleID] = concat(config, pureConfig)

  val conseilSmokeTestsInclude: Seq[ModuleID] = concat(config, http4s, circe, cats)

  private def concat(xs: Seq[ModuleID]*): Seq[ModuleID] = xs.reduceLeft(_ ++ _)

}
