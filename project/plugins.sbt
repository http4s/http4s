libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11"

// https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "4.2.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("com.github.tkawachi" % "sbt-doctest" % "0.10.0")
addSbtPlugin("org.http4s" % "sbt-http4s-org" % "0.14.10")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.15")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.4")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.10")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.7")
