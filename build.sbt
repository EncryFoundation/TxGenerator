name := "TransactionsGenerator"
version := "0.8.3"
organization := "org.encryFoundation"

val akkaHttpVersion = "10.1.10"
val akkaVersion     = "2.6.0"

val catsVersion   = "2.0.0"
val fs2Version    = "2.0.0"
val http4sVersion = "0.20.12"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-core"           % catsVersion,
  "co.fs2"               %% "fs2-io"              % fs2Version,
  "org.scodec"           %% "scodec-stream"       % "2.0.0",
  "org.http4s"           %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"           %% "http4s-circe"        % http4sVersion,
  "org.http4s"           %% "http4s-dsl"          % http4sVersion,
  "io.chrisdavenport"    %% "log4cats-slf4j"      % "0.4.0-M2",
 // "org.scalameta" %% "semanticdb-scalac" % "4.2.5",
  "com.typesafe.akka"    %% "akka-http"           % akkaHttpVersion,
  "com.typesafe.akka"    %% "akka-actor"          % akkaVersion,
  "com.typesafe.akka"    %% "akka-stream"         % akkaVersion,
  "org.influxdb"         % "influxdb-java"        % "2.10",
  "com.iheart"           %% "ficus"               % "1.4.7",
  "org.encry"            %% "encry-common"        % "0.9.2",
  "com.google.guava"     % "guava"                % "27.1-jre",
  "com.thesamet.scalapb" %% "scalapb-runtime"     % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "commons-net"          % "commons-net"          % "3.3"
)

scalacOptions ++= List(
  "-feature",
  "-language:higherKinds",
  "-Xlint",
  "-Yrangepos"
  //"-Ywarn-unused"
)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Typesafe maven releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value / "protobuf"
)
