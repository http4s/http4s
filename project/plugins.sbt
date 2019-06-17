resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"

addSbtPlugin("com.earldouglas"            %  "xsbt-web-plugin"           % "4.0.2")
addSbtPlugin("com.eed3si9n"               %  "sbt-buildinfo"             % "0.9.0")
addSbtPlugin("com.eed3si9n"               %  "sbt-unidoc"                % "0.4.2")
addSbtPlugin("com.github.cb372"           %  "sbt-explicit-dependencies" % "0.2.9")
addSbtPlugin("com.github.gseitz"          %  "sbt-release"               % "1.0.11")
addSbtPlugin("com.github.tkawachi"        %  "sbt-doctest"               % "0.9.5")
addSbtPlugin("com.jsuereth"               %  "sbt-pgp"                   % "1.1.2")
addSbtPlugin("com.lucidchart"             %  "sbt-scalafmt-coursier"     % "1.15")
addSbtPlugin("com.timushev.sbt"           %  "sbt-updates"               % "0.4.0")
addSbtPlugin("com.typesafe"               %  "sbt-mima-plugin"           % "0.3.0")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-ghpages"               % "0.6.3")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-site"                  % "1.4.0")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-twirl"                 % "1.4.2")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-native-packager"       % "1.3.22")
addSbtPlugin("io.get-coursier"            %  "sbt-coursier"              % "1.0.3")
addSbtPlugin("io.github.davidgregory084"  %  "sbt-tpolecat"              % "0.1.6")
addSbtPlugin("io.spray"                   %  "sbt-revolver"              % "0.9.1")
addSbtPlugin("org.scalastyle"             %% "scalastyle-sbt-plugin"     % "1.0.0")
addSbtPlugin("org.tpolecat"               %  "tut-plugin"                % "0.6.12")
addSbtPlugin("org.xerial.sbt"             %  "sbt-sonatype"              % "2.4")
addSbtPlugin("pl.project13.scala"         %  "sbt-jmh"                   % "0.3.6")
