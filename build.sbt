name := "akka-xlsx"

lazy val AkkaVersion = "2.6.16"
lazy val AkkaContribVersion = "0.11"
lazy val AlpakkaXmlVersion = "3.0.3"

lazy val ScalatestVersion = "3.2.3"
lazy val ScalatestPlusVersion = "3.2.3.0"
lazy val ScalacheckVersion = "1.15.2"

lazy val commonSettings = Seq(
  updateOptions := updateOptions.value.withGigahorse(false),
  scalaVersion := "2.13.6",
  organization := "de.envisia.akka",
  scalacOptions ++= Seq(
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
      "com.lightbend.akka" %% "akka-stream-alpakka-xml" % AlpakkaXmlVersion,
      "com.typesafe.akka" %% "akka-stream-contrib" % AkkaContribVersion,
    ) ++ Seq(
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
      "org.scalatest" %% "scalatest" % ScalatestVersion,
      "org.scalatestplus" %% "scalacheck-1-15" % ScalatestPlusVersion,
      "org.scalacheck" %% "scalacheck" % ScalacheckVersion,
    ).map(_ % Test)
  ))

licenses += ("Apache License 2", new URL("http://www.apache.org/licenses/LICENSE-2.0.html"))
developers += Developer(
  "schmitch",
  "Christian Schmitt",
  "c.schmitt@briefdomain.de",
  new URL("https://github.com/schmitch/")
)
