resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.1.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.1")
// Upgrade to 1.3.5 blocked on https://github.com/scoverage/sbt-scoverage/issues/146
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.2.0") 
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.2.0")
addSbtPlugin("io.gatling" % "gatling-sbt" % "2.1.5")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.4.4")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.6")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")
