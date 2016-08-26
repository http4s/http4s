import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.pgp.PgpKeys._
import sbtunidoc.Plugin.UnidocKeys._

// Global settings
organization in ThisBuild := "org.http4s"
version      in ThisBuild := scalazCrossBuild("0.15.0-SNAPSHOT", scalazVersion.value)
apiVersion   in ThisBuild <<= version.map(extractApiVersion)
scalaVersion in ThisBuild := "2.10.6"
// The build supports both scalaz `7.1.x` and `7.2.x`. Simply run
// `set scalazVersion in ThisBuild := "7.2.4"` to change which version of scalaz
// is used to build the project.
scalazVersion in ThisBuild := "7.1.8"
crossScalaVersions in ThisBuild <<= scalaVersion(Seq(_, "2.11.8"))

// Root project
name := "root"
description := "A minimal, Scala-idiomatic library for HTTP"
noPublishSettings

lazy val core = libraryProject("core")
  .enablePlugins(BuildInfoPlugin)
  .settings(
    description := "Core http4s library for servers and clients",
    buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, apiVersion),
    buildInfoPackage <<= organization,
    libraryDependencies ++= Seq(
      http4sWebsocket,
      log4s,
      macroCompat,
      parboiled,
      scalaCompiler(scalaVersion.value) % "provided",
      scalaReflect(scalaVersion.value) % "provided",
      scalazCore(scalazVersion.value),
      scalazStream(scalazVersion.value)
    ),
    macroParadiseSetting
  )

lazy val server = libraryProject("server")
  .settings(
    description := "Base library for building http4s servers",
    libraryDependencies += metricsCore
  )
  .dependsOn(core % "compile;test->test", theDsl % "test->compile")

lazy val client = libraryProject("client")
  .settings(
    description := "Base library for building http4s clients",
    libraryDependencies += jettyServlet % "test"
  )
  .dependsOn(core % "compile;test->test", server % "test->compile", theDsl % "test->compile")

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
    description := "Base library for binding blaze to http4s clients and servers",
    libraryDependencies += blaze
  )
  .dependsOn(core)

lazy val blazeServer = libraryProject("blaze-server")
  .settings(
    description := "blaze implementation for http4s servers"
  )
  .dependsOn(blazeCore % "compile;test->test", server % "compile;test->test")

lazy val blazeClient = libraryProject("blaze-client")
  .settings(
    description := "blaze implementation for http4s clients"
  )
  .dependsOn(blazeCore % "compile;test->test", client % "compile;test->test")

lazy val asyncHttpClient = libraryProject("async-http-client")
  .settings(
    description := "async http client implementation for http4s clients",
    libraryDependencies ++= Seq(
      Http4sBuild.asyncHttpClient,
      reactiveStreamsTck % "test"
    )
  )
  .dependsOn(core % "compile;test->test", client % "compile;test->test")

lazy val servlet = libraryProject("servlet")
  .settings(
    description := "Portable servlet implementation for http4s servers",
    libraryDependencies ++= Seq(
      javaxServletApi % "provided",
      jettyServer % "test",
      jettyServlet % "test"
    )
  )
  .dependsOn(server % "compile;test->test")

lazy val jetty = libraryProject("jetty")
  .settings(
    description := "Jetty implementation for http4s servers",
    libraryDependencies ++= Seq(
      metricsJetty9,
      jettyServlet
    )
  )
  .dependsOn(servlet % "compile;test->test", theDsl % "test->test")

lazy val tomcat = libraryProject("tomcat")
  .settings(
    description := "Tomcat implementation for http4s servers",
    libraryDependencies ++= Seq(
      metricsServlet,
      tomcatCatalina,
      tomcatCoyote
    )
  )
  .dependsOn(servlet % "compile;test->test")

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryProject("dsl")
  .settings(
    description := "Simple DSL for writing http4s services"
  )
  .dependsOn(core % "compile;test->test")

lazy val jawn = libraryProject("jawn")
  .settings(
    description := "Base library to parse JSON to various ASTs for http4s",
    libraryDependencies += jawnStreamz(scalazVersion.value)
  )
  .dependsOn(core % "compile;test->test")

lazy val argonaut = libraryProject("argonaut")
  .settings(
    description := "Provides Argonaut codecs for http4s",
    libraryDependencies ++= Seq(
      Http4sBuild.argonaut(scalazVersion.value),
      jawnParser
    )
  )
  .dependsOn(core % "compile;test->test", jawn % "compile;test->test")

lazy val circe = libraryProject("circe")
  .settings(
    description := "Provides Circe codecs for http4s",
    libraryDependencies += circeJawn
  )
  .dependsOn(core % "compile;test->test", jawn % "compile;test->test")

lazy val json4s = libraryProject("json4s")
  .settings(
    description := "Base library for json4s codecs for http4s",
    libraryDependencies ++= Seq(
      jawnJson4s,
      json4sCore
    )
  )
  .dependsOn(jawn % "compile;test->test")

lazy val json4sNative = libraryProject("json4s-native")
  .settings(
    description := "Provides json4s-native codecs for http4s",
    libraryDependencies += Http4sBuild.json4sNative,
    scalacOptions := {
      VersionNumber(scalaVersion.value).numbers match {
        case Seq(2, y, _) if y >= 11 =>
          // scala.text.Document is deprecated starting in 2.10
          scalacOptions.value filterNot (_ == "-Xfatal-warnings")
        case _ =>
          scalacOptions.value
      }
    }
  )
  .dependsOn(json4s % "compile;test->test")

lazy val json4sJackson = libraryProject("json4s-jackson")
  .settings(
    description := "Provides json4s-jackson codecs for http4s",
    libraryDependencies += Http4sBuild.json4sJackson
  )
  .dependsOn(json4s % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
    description := "Provides scala-xml codecs for http4s",
    libraryDependencies <++= scalaVersion (VersionNumber(_).numbers match {
      case Seq(2, scalaMajor, _*) if scalaMajor >= 11 => Seq(Http4sBuild.scalaXml)
      case _ => Seq.empty
    })
  )
  .dependsOn(core % "compile;test->test")

lazy val twirl = http4sProject("twirl")
  .settings(
    description := "Twirl template support for http4s",
    libraryDependencies += twirlApi
  )
  .enablePlugins(SbtTwirl)
  .dependsOn(core % "compile;test->test")

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(
    description := "Benchmarks for http4s"
  )
  .dependsOn(core)

lazy val loadTest = http4sProject("load-test")
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(
    description := "Load tests for http4s servers",
    libraryDependencies ++= Seq(
      gatlingHighCharts,
      gatlingTest
    ).map(_ % "it,test")
  )
  .enablePlugins(GatlingPlugin)

lazy val docs = http4sProject("docs")
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(unidocSettings)
  .settings(site.settings)
  .settings(ghpages.settings)
  .settings(tutSettings)
  .settings(
    description := "Documentation for http4s",
    autoAPIMappings := true,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        bench,
        examples,
        examplesBlaze,
        examplesJetty,
        examplesTomcat,
        examplesWar,
        loadTest
      ),
    // documentation source code linking
    scalacOptions in (Compile,doc) <++= (version, apiVersion, scmInfo, baseDirectory in ThisBuild) map {
      case (v, (maj,min), Some(s), b) => 
        val sourceTemplate =
          if (v.endsWith("SNAPSHOT"))
            s"${s.browseUrl}/tree/master€{FILE_PATH}.scala"
          else 
            s"${s.browseUrl}/tree/v$maj.$min.0€{FILE_PATH}.scala"
        Seq("-implicits",
            "-doc-source-url", sourceTemplate,
            "-sourcepath", b.getAbsolutePath)
      case _ => Seq.empty
    },
    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.json" | "*.md" | "CNAME" | "_config.yml",
    siteMappings := {
      if (Http4sGhPages.buildMainSite) siteMappings.value
      else Seq.empty
    },
    siteMappings <++= (tut, apiVersion) map { case (t, (major, minor)) =>
      for ((f, d) <- t) yield (f, s"docs/$major.$minor/$d")
    },
    siteMappings <++= (mappings in (ScalaUnidoc, packageDoc), apiVersion) map {
      case (m, (major, minor)) => for ((f, d) <- m) yield (f, s"api/$major.$minor/$d")
    },
    cleanSite <<= Http4sGhPages.cleanSite0,
    synchLocal <<= Http4sGhPages.synchLocal0,
    git.remoteRepo := "git@github.com:http4s/http4s.git",
    ghpagesNoJekyll := false
  )
  .dependsOn(client, core, theDsl, blazeServer, blazeClient, argonaut)

lazy val examples = http4sProject("examples")
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(
    description := "Common code for http4s examples",
    libraryDependencies ++= Seq(
      circeGeneric,
      logbackClassic % "runtime",
      jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
    )
  )
  .dependsOn(server, theDsl, circe, scalaXml, twirl)
  .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    fork := true,
    libraryDependencies ++= Seq(alpnBoot, metricsJson),
    macroParadiseSetting,
    javaOptions in run <++= (managedClasspath in Runtime) map { attList =>
      for {
        file <- attList.map(_.data)
        path = file.getAbsolutePath if path.contains("jetty.alpn")
      } yield { s"-Xbootclasspath/p:${path}" }
    }
  )
  .dependsOn(blazeServer, blazeClient)

lazy val examplesJetty = exampleProject("examples-jetty")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Jetty",
    fork := true,
    libraryDependencies += metricsServlets,
    mainClass in reStart := Some("com.example.http4s.jetty.JettyExample")
  )
  .dependsOn(jetty)

lazy val examplesTomcat = exampleProject("examples-tomcat")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Tomcat",
    fork := true,
    libraryDependencies += metricsServlets,
    mainClass in reStart := Some("com.example.http4s.tomcat.TomcatExample")
  )
  .dependsOn(tomcat)

// Run this with jetty:start
lazy val examplesWar = exampleProject("examples-war")
  .enablePlugins(JettyPlugin)
  .settings(
    description := "Example of a WAR deployment of an http4s service",
    fork := true,
    libraryDependencies ++= Seq(
      javaxServletApi % "provided",
      logbackClassic % "runtime"
    )
  )
  .dependsOn(servlet)

def http4sProject(name: String) = Project(name, file(name))
  .settings(commonSettings)
  .settings(projectMetadata)
  .settings(publishSettings)
  .settings(
    moduleName := s"http4s-$name",
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2,"xonly", "failtrace")
  )

def libraryProject(name: String) = http4sProject(name)
  .settings(mimaSettings)

def exampleProject(name: String) = http4sProject(name)
  .in(file(name.replace("examples-", "examples/")))
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .dependsOn(examples)

lazy val projectMetadata = Seq(
  homepage := Some(url("http://http4s.org/")),
  startYear := Some(2013),
  licenses := Seq(
    "Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  scmInfo := {
    val base = "github.com/http4s/http4s"
    Some(ScmInfo(url(s"https://$base"), s"scm:git:https://$base", Some(s"scm:git:git@$base")))
  },
  pomExtra := (
    <developers>
      <developer>
        <id>rossabaker</id>
        <name>Ross A. Baker</name>
        <email>ross@rossabaker.com</email>
      </developer>
      <developer>
        <id>casualjim</id>
        <name>Ivan Porto Carrero</name>
        <email>ivan@flanders.co.nz</email>
        <url>http://flanders.co.nz</url>
      </developer>
      <developer>
        <id>brycelane</id>
        <name>Bryce L. Anderson</name>
        <email>bryce.anderson22@gmail.com</email>
      </developer>
      <developer>
        <id>before</id>
        <name>André Rouél</name>
      </developer>
      <developer>
        <id>julien-truffaut</id>
        <name>Julien Truffaut</name>
      </developer>
      <developer>
        <id>kryptt</id>
        <name>Rodolfo Hansen</name>
      </developer>
    </developers>
  )
)

lazy val commonSettings = Seq(
  jvmTarget <<= scalaVersion.map {
    VersionNumber(_).numbers match {
      case Seq(2, 10, _*) => "1.7"
      case _ => "1.8"
    }
  },
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    s"-target:jvm-${jvmTarget.value}",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yinline-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"
  ),
  scalacOptions := {
    // We're deprecation-clean across Scala versions, but not across scalaz
    // versions.  This is not worth maintaining a branch.
    VersionNumber(scalazVersion.value).numbers match {
      case Seq(7, 1, _) =>
        scalacOptions.value
      case _ =>
        // This filtering does not trigger when scalazVersion is changed in a
        // running SBT session.  Help wanted.
        scalacOptions.value filterNot (_ == "-Xfatal-warnings")
    }
  },
  /* disabled because SI-7529
  scalacOptions <++= scalaVersion.map { v =>
    if (delambdafyOpts(v)) Seq(
      "-Ybackend:GenBCode", 
      "-Ydelambdafy:method",
      "-Yopt:l:classpath"
    ) else Seq.empty
  },
   */
  javacOptions <++= jvmTarget.map { jvm => Seq(
    "-source", jvm,
    "-target", jvm,
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  )},
  resolvers ++= Seq(
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
  ),
  libraryDependencies <++= scalaVersion(v =>
    if (delambdafyOpts(v)) Seq("org.scala-lang.modules" %% "scala-java8-compat" % "0.5.0")
    else Seq.empty
  ),
  libraryDependencies <++= scalazVersion(sz => Seq(
    discipline,
    logbackClassic,
    scalazScalacheckBinding(sz),
    specs2Core(sz),
    specs2MatcherExtra(sz),
    specs2Scalacheck(sz)
  ).map(_ % "test"))
)

lazy val publishSettings = Seq(
  credentials ++= sonatypeEnvCredentials
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishSigned := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val noCoverageSettings = Seq(
  ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*"
)

lazy val mimaSettings = Seq(
  mimaFailOnProblem <<= version.zipWith(scalazVersion)(compatibleVersion(_, _).isDefined),
  previousArtifact <<= (version, organization, scalaBinaryVersion, moduleName, scalazVersion)((ver, org, binVer, mod, sz) => compatibleVersion(ver, sz) map {
    org % s"${mod}_${binVer}" % _
  }),
  binaryIssueFilters ++= {
    import com.typesafe.tools.mima.core._
    import com.typesafe.tools.mima.core.ProblemFilters._
    Seq(
      exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection.this")
    )
  }
)

// Check whether to enable java 8 type lambdas
// https://github.com/scala/make-release-notes/blob/2.11.x/experimental-backend.md
// Minimum scala version is 2.11.8 due to sbt/sbt#2076
def delambdafyOpts(v: String): Boolean = VersionNumber(v).numbers match {
  case Seq(2, 11, x, _*) if x > 7 => true
  case _ => false
}
