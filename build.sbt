name := "akka-xlsx"

lazy val PekkoXmlVersion      = "1.1.0"
lazy val PekkoTestkitVersion  = "1.1.3"
lazy val ScalatestVersion     = "3.2.19"
lazy val ScalatestPlusVersion = "3.2.19.0"

lazy val commonSettings = Seq(
  updateOptions := updateOptions.value.withGigahorse(false),
  scalaVersion := "2.13.16",
  organization := "de.envisia.akka",
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
  ),
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-o"),
  publishMavenStyle := true,
  pomIncludeRepository := (_ => false),
)

lazy val root = (project in file(".")).settings(
  commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-connectors-xml" % PekkoXmlVersion,
    ) ++ Seq(
      "org.apache.pekko"  %% "pekko-stream-testkit" % PekkoTestkitVersion,
      "org.scalatest"     %% "scalatest"            % ScalatestVersion,
      "org.scalatestplus" %% "scalacheck-1-18"      % ScalatestPlusVersion,
    ).map(_ % Test)
  )
)

licenses += ("Apache License 2", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
developers += Developer(
  "schmitch",
  "Christian Schmitt",
  "c.schmitt@briefdomain.de",
  url("https://github.com/schmitch/")
)
