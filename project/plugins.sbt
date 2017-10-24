resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.earldouglas"    %  "xsbt-web-plugin"       % "4.0.0")
addSbtPlugin("com.lucidchart"     %  "sbt-scalafmt-coursier" % "1.14")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.typesafe"       %  "sbt-mima-plugin"       % "0.1.14")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-native-packager"   % "1.2.2")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-twirl"             % "1.3.12")
addSbtPlugin("io.gatling"         %  "gatling-sbt"           % "2.2.2")
addSbtPlugin("io.get-coursier"    %  "sbt-coursier"          % "1.0.0-RC11")
addSbtPlugin("io.spray"           %  "sbt-revolver"          % "0.9.0")
addSbtPlugin("io.verizon.build"   %  "sbt-rig"               % "5.0.39")
addSbtPlugin("pl.project13.scala" %  "sbt-jmh"               % "0.2.27")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
