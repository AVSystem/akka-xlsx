import ReleaseTransformations._

name := "akka-xlsx"

lazy val AkkaVersion        = "2.5.12"
lazy val AkkaContribVersion = "0.9"
lazy val AlpakkaXmlVersion  = "0.19"

lazy val ScalatestVersion  = "3.0.5"
lazy val ScalacheckVersion = "1.14.0"

lazy val commonSettings = Seq(
  updateOptions := updateOptions.value.withGigahorse(false),
  scalaVersion := "2.12.6",
  organization := "de.envisia.akka",
  scalacOptions in (Compile, doc) ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation"
  ),
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o"),
  publishMavenStyle := true,
  pomIncludeRepository := (_ => false),
  publishTo := Some("envisia" at "https://nexus.envisia.io/repository/public/")
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-stream"             % AkkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-xml" % AlpakkaXmlVersion,
      "com.typesafe.akka"  %% "akka-stream-contrib"     % AkkaContribVersion,
    ) ++ Seq(
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
      "org.scalatest"     %% "scalatest"           % ScalatestVersion,
      "org.scalacheck"    %% "scalacheck"          % ScalacheckVersion,
    ).map(_ % Test)
  )

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
