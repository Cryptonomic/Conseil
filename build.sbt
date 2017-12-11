name := "Conseil"
version := "0.0.1"
scalaVersion := "2.12.4"

val akkaHttpVersion = "10.0.11"

libraryDependencies  ++=  Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "org.scalaj" % "scalaj-http_2.12" % "2.3.0"
)

assemblyOutputPath in assembly := file("/tmp/conseil.jar")
