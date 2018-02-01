resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.earldouglas"    %  "xsbt-web-plugin"       % "4.0.1")
addSbtPlugin("com.lucidchart"     %  "sbt-scalafmt-coursier" % "1.15")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.typesafe"       %  "sbt-mima-plugin"       % "0.1.18")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-native-packager"   % "1.3.2")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-twirl"             % "1.3.13")
addSbtPlugin("io.gatling"         %  "gatling-sbt"           % "2.2.2")
addSbtPlugin("io.get-coursier"    %  "sbt-coursier"          % "1.0.0")
addSbtPlugin("io.spray"           %  "sbt-revolver"          % "0.9.1")
addSbtPlugin("io.verizon.build"   %  "sbt-rig"               % "5.0.39")
addSbtPlugin("org.tpolecat"       %  "tut-plugin"            % "0.6.1")
addSbtPlugin("pl.project13.scala" %  "sbt-jmh"               % "0.3.2")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"
