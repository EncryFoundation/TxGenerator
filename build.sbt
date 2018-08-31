name := "TxGenerator"
version := "0.1.0"
organization := "org.encry"

scalaVersion := "2.12.6"

val akkaVersion = "2.5.13"
val akkaHttpVersion = "10.0.9"
val logbackVersion = "1.2.3"

val loggingDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "org.influxdb" % "influxdb-java" % "2.10",
  "com.iheart" %% "ficus" % "1.4.3",
  "org.encry" %% "encry-common" % "0.8.3"
) ++ loggingDependencies

resolvers ++= Seq("Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Typesafe maven releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")