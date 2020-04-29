import sbt._

object Dependencies {

  private val akkaVersion = "2.5.21"
  private val akkaHttpVersion = "10.1.8"
  private val akkaHttpJsonVersion = "1.26.0"
  private val slickVersion = "3.3.0"
  private val catsVersion = "1.6.0"
  private val monocleVersion = "1.5.1-cats"
  private val endpointsVersion = "0.9.0"
  private val circeVersion = "0.11.1"
  private val http4sVersion = "0.20.10"
  private val silencerVersion = "1.4.4"
  private val kantanCsvVersion = "0.6.0"

  //TODO Split these dependencies into smaller pieces (logical one)
  val conseilCommonInclude = Seq(
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
    "org.scalatest"                %% "scalatest"                     % "3.0.5" % Test,
    "com.stephenn"                 %% "scalatest-json-jsonassert"     % "0.0.3" % Test,
    "org.scalamock"                %% "scalamock"                     % "4.1.0" % Test,
    "org.testcontainers"           % "postgresql"                     % "1.12.3" % Test,
    "com.softwaremill.diffx"       %% "diffx-scalatest"               % "0.3.3" % Test,
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
  )

  val conseilCommonExclude = Seq(
    "org.consensusresearch" %% "scrypto"
  )

  val conseilCommonTestKitInclude = Seq(
    "com.typesafe.slick" %% "slick"     % slickVersion exclude ("org.reactivestreams", "reactive-streams") exclude ("com.typesafe", "config") exclude ("org.slf4j", "slf4j-api"),
    "com.typesafe"       % "config"     % "1.3.3",
    "org.scalatest"      %% "scalatest" % "3.0.5",
    "org.testcontainers" % "postgresql" % "1.12.3"
  )

  val conseilApiInclude = Seq(
    "org.scalamock"          %% "scalamock"         % "4.1.0"         % Test,
    "com.typesafe.akka"      %% "akka-testkit"      % akkaVersion     % Test exclude ("com.typesafe", "config"),
    "com.typesafe.akka"      %% "akka-http-testkit" % akkaHttpVersion % Test exclude ("com.typesafe", "config"),
    "org.scalatest"          %% "scalatest"         % "3.0.5"         % Test,
    "com.softwaremill.diffx" %% "diffx-scalatest"   % "0.3.3"         % Test,
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
  )

  val conseilLorreInclude = Seq(
    "org.scalamock" %% "scalamock" % "4.1.0" % Test,
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
  )

  val conseilSmokeTestsInclude = Seq(
    "org.typelevel" %% "cats-core"           % catsVersion,
    "org.http4s"    %% "http4s-blaze-client" % http4sVersion,
    "org.http4s"    %% "http4s-dsl"          % http4sVersion,
    "org.http4s"    %% "http4s-circe"        % http4sVersion,
    "io.circe"      %% "circe-core"          % circeVersion,
    "io.circe"      %% "circe-parser"        % circeVersion
  )

}
