resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"

addSbtPlugin("com.earldouglas"            %  "xsbt-web-plugin"           % "4.2.0")
addSbtPlugin("com.eed3si9n"               %  "sbt-buildinfo"             % "0.9.0")
addSbtPlugin("com.eed3si9n"               %  "sbt-unidoc"                % "0.4.3")
addSbtPlugin("com.geirsson"               %  "sbt-ci-release"            % "1.5.3")
addSbtPlugin("com.github.cb372"           %  "sbt-explicit-dependencies" % "0.2.13")
addSbtPlugin("com.github.tkawachi"        %  "sbt-doctest"               % "0.9.6")
addSbtPlugin("com.timushev.sbt"           %  "sbt-updates"               % "0.5.0")
addSbtPlugin("com.typesafe"               %  "sbt-mima-plugin"           % "0.7.0")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-ghpages"               % "0.6.3")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-site"                  % "1.4.0")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-twirl"                 % "1.5.0")
addSbtPlugin("com.typesafe.sbt"           %  "sbt-native-packager"       % "1.7.2")
addSbtPlugin("de.heikoseeberger"          %  "sbt-header"                % "5.6.0")
addSbtPlugin("io.github.davidgregory084"  %  "sbt-tpolecat"              % "0.1.11")
addSbtPlugin("io.spray"                   %  "sbt-revolver"              % "0.9.1")
addSbtPlugin("org.scalameta"              %  "sbt-scalafmt"              % "2.4.0")
addSbtPlugin("pl.project13.scala"         %  "sbt-jmh"                   % "0.3.7")
addSbtPlugin("org.scalameta"              %  "sbt-mdoc"                  % "2.1.5")
