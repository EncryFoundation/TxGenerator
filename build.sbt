name         := "TransactionsGenerator"
version      := "0.8.3"
organization := "org.encryFoundation"

val logbackVersion  = "1.2.3"
val catsVersion     = "1.0.1"
val akkaHttpVersion = "10.0.9"
val akkaVersion     = "2.5.13"

scalaVersion := "2.12.6"

val loggingDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.0",
  "ch.qos.logback"             % "logback-classic" % logbackVersion,
  "ch.qos.logback"             % "logback-core"    % logbackVersion
)

val testingDependencies = Seq(
  "com.typesafe.akka" %% "akka-testkit"       % "2.4.+"         % Test,
  "com.typesafe.akka" %% "akka-http-testkit"  % akkaHttpVersion % Test,
  "org.scalatest"     %% "scalatest"          % "3.0.3"         % Test,
  "org.scalacheck"    %% "scalacheck"         % "1.13.+"        % Test,
  "org.mockito"       % "mockito-core"        % "2.19.1"        % Test
)

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-http"       % akkaHttpVersion,
  "com.typesafe.akka"    %% "akka-actor"      % akkaVersion,
  "com.typesafe.akka"    %% "akka-stream"     % akkaVersion,
  "org.influxdb"         % "influxdb-java"    % "2.10",
  "com.iheart"           %% "ficus"           % "1.4.3",
  "org.encry"            %% "encry-common"    % "0.8.9",
  "org.typelevel"        % "cats-core_2.12"   % "1.0.1",
  "org.typelevel"        % "cats-kernel_2.12" % "1.0.1",
  "org.typelevel"        % "cats-macros_2.12" % "1.0.1",
  "com.google.guava"     % "guava"            % "27.1-jre",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "commons-net"          % "commons-net"      % "3.3"
) ++ loggingDependencies ++ testingDependencies

scalacOptions += "-Ypartial-unification"

scalacOptions += "-language:higherKinds"


resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Typesafe maven releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value / "protobuf"
)