libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11"

addDependencyTreePlugin

// https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.0")
addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "4.2.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.github.tkawachi" % "sbt-doctest" % "0.10.0")
addSbtPlugin("org.http4s" % "sbt-http4s-org" % "0.13.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.1")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
