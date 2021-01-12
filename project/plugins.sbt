logLevel := Level.Warn

// Dependency Resolution
updateOptions := updateOptions.value.withGigahorse(false)

// Publishing
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.2")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")