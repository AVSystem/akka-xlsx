import ReleaseTransformations._

name := "akka-xlsx"

lazy val AkkaVersion        = "2.5.31"
lazy val AkkaContribVersion = "0.10"
lazy val AlpakkaXmlVersion  = "2.0.2"

lazy val ScalatestVersion  = "3.0.9"
lazy val ScalacheckVersion = "1.15.2"

lazy val commonSettings = Seq(
  updateOptions := updateOptions.value.withGigahorse(false),
  scalaVersion := "2.13.4",
  organization := "de.envisia.akka",
  scalacOptions ++= Seq(
    "-target:jvm-1.11",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
  ),
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o"),
  publishMavenStyle := true,
  pomIncludeRepository := (_ => false),
)

lazy val root = (project in file("."))
  .settings(commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-stream"             % AkkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-xml" % AlpakkaXmlVersion,
      "com.typesafe.akka"  %% "akka-stream-contrib"     % AkkaContribVersion,
    ) ++ Seq(
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
      "org.scalatest"     %% "scalatest"           % ScalatestVersion,
      "org.scalacheck"    %% "scalacheck"          % ScalacheckVersion,
    ).map(_ % Test)
  ))

licenses += ("Apache License 2", new URL("http://www.apache.org/licenses/LICENSE-2.0.html"))
developers += Developer(
  "schmitch",
  "Christian Schmitt",
  "c.schmitt@briefdomain.de",
  new URL("https://github.com/schmitch/")
)

releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
