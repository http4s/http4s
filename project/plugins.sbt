addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.7")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.3.2")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.3")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.5")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.6")
