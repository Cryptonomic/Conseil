import sbt._

object Dependencies {

  private object Versions {
    val typesafeConfig = "1.3.3"
    val pureConfig = "0.10.2"
    val scopt = "4.0.0-RC2"

    val logback = "1.2.3"
    val logstashLogback = "5.3"
    val scalaLogging = "3.7.2"

    val akka = "2.5.21"
    val akkaHttp = "10.1.8"
    val akkaHttpJson = "1.26.0"
    val akkaHttpCors = "0.3.4"

    val slick = "3.3.0"
    val slickPG = "0.18.0"
    val slickEffect = "0.1.0"
    val postgres = "42.1.4"

    val endpoints = "0.9.0"
    val cats = "1.6.0"
    val mouse = "0.20"
    val monocle = "1.5.1-cats"
    val circe = "0.11.1"
    val http4s = "0.20.10"

    val silencer = "1.4.4"
    val kantanCsv = "0.6.0"
    val jackson = "2.9.6"
    val apacheCommonText = "1.7"
    val radixTree = "0.5.1"

    val scalaTest = "3.0.5"
    val scalaTestJson = "0.0.3"
    val scalaMock = "4.1.0"
    val testContainerPostgres = "1.12.3"
    val diffX = "0.3.3"

    val libsodiumJna = "1.0.4"
    val jna = "5.5.0"

    val chimney = "0.3.1"
    val bitcoin = "0.9.18-SNAPSHOT"
    val scrypto = "2.1.7"
    val scorex = "0.1.6"
  }

  private val config = Seq("com.typesafe" % "config" % Versions.typesafeConfig)

  private val pureConfig = Seq("com.github.pureconfig" %% "pureconfig" % Versions.pureConfig)

  private val scopt = Seq("com.github.scopt" %% "scopt" % Versions.scopt)

  private val logging = Seq(
    "ch.qos.logback"             % "logback-classic"          % Versions.logback,
    "net.logstash.logback"       % "logstash-logback-encoder" % Versions.logstashLogback,
    "com.typesafe.scala-logging" %% "scala-logging"           % Versions.scalaLogging
  )

  private val akka = Seq(
    "com.typesafe.akka" %% "akka-actor"   % Versions.akka exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-stream"  % Versions.akka exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-testkit" % Versions.akka % Test exclude ("com.typesafe", "config")
  )

  private val akkaHttp = Seq(
    "com.typesafe.akka" %% "akka-http"         % Versions.akkaHttp exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-http-caching" % Versions.akkaHttp exclude ("com.typesafe", "config"),
    "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp % Test exclude ("com.typesafe", "config")
  )

  private val akkaHttpJson = Seq(
    "de.heikoseeberger" %% "akka-http-circe"   % Versions.akkaHttpJson exclude ("com.typesafe.akka", "akka-http"),
    "de.heikoseeberger" %% "akka-http-jackson" % Versions.akkaHttpJson exclude ("com.fasterxml.jackson.core", "jackson-databind") exclude ("com.typesafe.akka", "akka-http")
  )

  private val akkaHttpCors = Seq(
    "ch.megard" %% "akka-http-cors" % Versions.akkaHttpCors exclude ("com.typesafe.akka", "akka-http")
  )

  private val slick = Seq(
    "com.typesafe.slick" %% "slick"          % Versions.slick exclude ("org.reactivestreams", "reactive-streams") exclude ("com.typesafe", "config") exclude ("org.slf4j", "slf4j-api"),
    "com.typesafe.slick" %% "slick-hikaricp" % Versions.slick exclude ("org.slf4j", "slf4j-api")
  )
  private val slickCodeGen = Seq("com.typesafe.slick" %% "slick-codegen" % Versions.slick)
  private val slickPG = Seq("com.github.tminglei"     %% "slick-pg"      % Versions.slickPG)
  private val slickEffect = Seq(
    "com.kubukoz" %% "slick-effect" % Versions.slickEffect exclude ("com.typesafe.slick", "slick")
  )

  private val postgres = Seq("org.postgresql" % "postgresql" % Versions.postgres)

  private val endpoints = Seq(
    "org.julienrf" %% "endpoints-algebra"             % Versions.endpoints,
    "org.julienrf" %% "endpoints-openapi"             % Versions.endpoints,
    "org.julienrf" %% "endpoints-json-schema-generic" % Versions.endpoints,
    "org.julienrf" %% "endpoints-json-schema-circe"   % Versions.endpoints,
    "org.julienrf" %% "endpoints-akka-http-server"    % Versions.endpoints
  )

  private val cats = Seq("org.typelevel"  %% "cats-core" % Versions.cats)
  private val mouse = Seq("org.typelevel" %% "mouse"     % Versions.mouse) // related to cats

  private val monocle = Seq(
    "com.github.julien-truffaut" %% "monocle-core"  % Versions.monocle exclude ("org.typelevel.cats", "cats-core"),
    "com.github.julien-truffaut" %% "monocle-macro" % Versions.monocle exclude ("org.typelevel.cats", "cats-core") exclude ("org.typelevel.cats", "cats-macros")
  )

  private val circe = Seq(
    "io.circe" %% "circe-core"           % Versions.circe,
    "io.circe" %% "circe-parser"         % Versions.circe,
    "io.circe" %% "circe-generic"        % Versions.circe,
    "io.circe" %% "circe-generic-extras" % Versions.circe
  )

  private val http4s = Seq(
    "org.http4s" %% "http4s-blaze-client" % Versions.http4s,
    "org.http4s" %% "http4s-dsl"          % Versions.http4s,
    "org.http4s" %% "http4s-circe"        % Versions.http4s
  )

  private val silencer = Seq(
    compilerPlugin("com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % Versions.silencer % Provided cross CrossVersion.full
  )

  private val kantanCsv = Seq(
    "com.nrinaudo" %% "kantan.csv-generic" % Versions.kantanCsv,
    "com.nrinaudo" %% "kantan.csv-java8"   % Versions.kantanCsv
  )

  private val jackson = Seq(
    "com.fasterxml.jackson.core"   % "jackson-databind"      % Versions.jackson exclude ("com.fasterxml.jackson.core", "jackson-annotations"),
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jackson
  )

  private val scalaTestCompile = Seq("org.scalatest" %% "scalatest" % Versions.scalaTest) // Dedicated for common-testkit
  private val scalaTest = scalaTestCompile.map(_     % Test)
  private val scalaTestJson = Seq("com.stephenn"     %% "scalatest-json-jsonassert" % Versions.scalaTestJson % Test)

  private val scalaMock = Seq("org.scalamock" %% "scalamock" % Versions.scalaMock % Test)

  private val postgresTestContainerCompile = Seq("org.testcontainers"    % "postgresql" % Versions.testContainerPostgres)
  private val postgresTestContainer = postgresTestContainerCompile.map(_ % Test)

  private val diffX = Seq("com.softwaremill.diffx" %% "diffx-scalatest" % Versions.diffX % Test)

  private val apacheCommonsText = Seq("org.apache.commons" % "commons-text" % Versions.apacheCommonText)

  private val radixTree = Seq("com.rklaehn" %% "radixtree" % Versions.radixTree)

  private val jna = Seq(
    "com.muquit.libsodiumjna" % "libsodium-jna" % Versions.libsodiumJna exclude ("org.slf4j", "slf4j-log4j12") exclude ("org.slf4j", "slf4j-api"),
    "net.java.dev.jna"        % "jna"           % Versions.jna //see https://github.com/muquit/libsodium-jna/#update-your-projects-pomxml
  )

  private val chimney = Seq("io.scalaland" %% "chimney" % Versions.chimney)

  private val bitcoin = Seq("fr.acinq" %% "bitcoin-lib" % Versions.bitcoin)

  private val scorex = Seq(
    "org.scorexfoundation" %% "scrypto"     % Versions.scrypto,
    "org.scorexfoundation" %% "scorex-util" % Versions.scorex
  )

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
      http4s,
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
    concat(config, logging, pureConfig, scopt, silencer, akka, akkaHttp, scalaTest, scalaMock, diffX)

  val conseilSchemaInclude: Seq[ModuleID] = concat(config, pureConfig)

  val conseilSmokeTestsInclude: Seq[ModuleID] = concat(config, http4s, circe, cats)

  private def concat(xs: Seq[ModuleID]*): Seq[ModuleID] = xs.reduceLeft(_ ++ _)

}
