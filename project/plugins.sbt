resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.earldouglas"    %  "xsbt-web-plugin"       % "3.0.1")
addSbtPlugin("com.eed3si9n"       %  "sbt-buildinfo"         % "0.7.0")
addSbtPlugin("com.eed3si9n"       %  "sbt-unidoc"            % "0.3.3")
addSbtPlugin("com.github.gseitz"  %  "sbt-release"           % "1.0.4")
addSbtPlugin("com.lucidchart"     %  "sbt-scalafmt-coursier" % "1.10")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin" % "0.8.0")
addSbtPlugin("com.typesafe"       %  "sbt-mima-plugin"       % "0.1.14")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-ghpages"           % "0.5.4")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-site"              % "1.2.1")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-twirl"             % "1.3.4")
addSbtPlugin("io.gatling"         %  "gatling-sbt"           % "2.1.5")
addSbtPlugin("io.get-coursier"    %  "sbt-coursier"          % "1.0.0-RC1")
addSbtPlugin("io.spray"           %  "sbt-revolver"          % "0.8.0")
addSbtPlugin("io.verizon.build"   %  "sbt-rig"               % "2.0.29")
addSbtPlugin("org.tpolecat"       %  "tut-plugin"            % "0.4.8")
addSbtPlugin("pl.project13.scala" %  "sbt-jmh"               % "0.2.24")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.7"
