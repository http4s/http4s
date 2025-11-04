libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.13"

// https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"

val scalaNativeVersion = "0.5.9"
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey]("scalaNativeVersion" -> scalaNativeVersion)
// Allow referencing from `build.sbt` as `buildinfo.BuildBuildInfo`
buildInfoObject := "BuildBuildInfo"
addSbtPlugin("org.scala-native" % "sbt-scala-native" % scalaNativeVersion)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "4.2.5")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("io.github.sbt-doctest" % "sbt-doctest" % "0.11.4")
addSbtPlugin("org.http4s" % "sbt-http4s-org" % "2.0.2")
// TODO: Remove after `sbt-http4s-org` updated to pull in 0.14.4
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.4")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.1")
addSbtPlugin("com.armanbilge" % "sbt-scala-native-config-brew" % "0.4.0")

libraryDependencySchemes += "com.lihaoyi" %% "geny" % VersionScheme.Always
