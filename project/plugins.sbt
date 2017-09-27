resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.earldouglas"    %  "xsbt-web-plugin"       % "3.0.1")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin" % "0.8.0")
addSbtPlugin("com.typesafe"       %  "sbt-mima-plugin"       % "0.1.14")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-twirl"             % "1.3.7")
addSbtPlugin("io.gatling"         %  "gatling-sbt"           % "2.1.5")
addSbtPlugin("io.get-coursier"    %  "sbt-coursier"          % "1.0.0-RC1")
addSbtPlugin("io.spray"           %  "sbt-revolver"          % "0.8.0")
addSbtPlugin("io.verizon.build"   %  "sbt-rig"               % "4.0.36")
addSbtPlugin("org.lyranthe.sbt"   %  "partial-unification"   % "1.1.0")
addSbtPlugin("pl.project13.scala" %  "sbt-jmh"               % "0.2.24")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.7"
