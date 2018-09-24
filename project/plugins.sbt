addSbtPlugin("org.xerial.sbt"   % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth"     % "sbt-pgp"      % "1.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git"      % "1.0.0")

addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("com.geirsson"     % "sbt-scalafmt"  % "1.4.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"   % "0.3.4")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.3.0")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.3")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.8.0"
