import org.http4s.build.Http4sPlugin._
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / organization := "org.http4s"

// Root project
name := "http4s"
description := "A minimal, Scala-idiomatic library for HTTP"
enablePlugins(PrivateProjectPlugin)

Global / cancelable := true

lazy val core = libraryProject("core")
  .enablePlugins(BuildInfoPlugin)
  .settings(
    scalaVersion := "2.13.0-M5",
    description := "Core http4s library for servers and clients",
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      scalaVersion,
      BuildInfoKey.map(http4sApiVersion) { case (_, v) => "apiVersion" -> v }
    ),
    buildInfoPackage := organization.value,
    resolvers += "Sonatype OSS Snapshots".at(
      "https://oss.sonatype.org/content/repositories/snapshots"),
    libraryDependencies ++= Seq(
      cats,
      fs2Io,
      log4s,
      parboiled,
      vault,
      scalaReflect(scalaOrganization.value, scalaVersion.value) % "provided",
      scalaCompiler(scalaOrganization.value, scalaVersion.value) % "provided",
      scalaCollectionCompat
    ),
    unmanagedSourceDirectories in Compile ++= {
      (unmanagedSourceDirectories in Compile).value.map { dir =>
        val sv = scalaVersion.value
        CrossVersion.partialVersion(sv) match {
          case Some((2, 13)) => file(dir.getPath ++ "-2.13")
          case _             => file(dir.getPath ++ "-2.11-2.12")
        }
      }
    },
  )

lazy val testing = libraryProject("testing")
  .settings(
    description := "Instances and laws for testing http4s code",
    libraryDependencies ++= Seq(
      catsEffectLaws,
      scalacheck(scalaVersion.value),
      specs2Matcher(scalaVersion.value)
    ),
  )
  .dependsOn(core)

// Defined outside core/src/test so it can depend on published testing
lazy val tests = libraryProject("tests")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Tests for core project",
  )
  .dependsOn(core, testing % "test->test")

lazy val server = libraryProject("server")
  .settings(
    description := "Base library for building http4s servers"
  )
  .dependsOn(core, testing % "test->test", theDsl % "test->compile")

lazy val prometheusMetrics = libraryProject("prometheus-metrics")
  .settings(
    description := "Support for Prometheus Metrics",
    libraryDependencies ++= Seq(
      prometheusCommon,
      prometheusHotspot,
      prometheusClient
    ),
  )
  .dependsOn(
    core % "compile->compile",
    theDsl % "compile->compile",
    testing % "test->test",
    server % "test->compile",
    client % "test->compile"
  )
lazy val client = libraryProject("client")
  .settings(
    description := "Base library for building http4s clients",
    libraryDependencies += jettyServlet % "test"
  )
  .dependsOn(
    core,
    testing % "test->test",
    server % "test->compile",
    theDsl % "test->compile",
    scalaXml % "test->compile")

lazy val dropwizardMetrics = libraryProject("dropwizard-metrics")
  .settings(
    description := "Support for Dropwizard Metrics",
    libraryDependencies ++= Seq(
      dropwizardMetricsCore,
      dropwizardMetricsJson
    ))
  .dependsOn(
    core % "compile->compile",
    testing % "test->test",
    theDsl % "test->compile",
    client % "test->compile",
    server % "test->compile"
  )

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
    description := "Base library for binding blaze to http4s clients and servers",
    libraryDependencies += blaze,
  )
  .dependsOn(core, testing % "test->test")

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
      Http4sPlugin.asyncHttpClient,
      fs2ReactiveStreams
    )
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val jettyClient = libraryProject("jetty-client")
  .settings(
    description := "jetty implementation for http4s clients",
    libraryDependencies ++= Seq(
      Http4sPlugin.jettyClient
    ),
    mimaPreviousArtifacts := Set.empty // remove me once merged
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val okHttpClient = libraryProject("okhttp-client")
  .settings(
    description := "okhttp implementation for http4s clients",
    libraryDependencies ++= Seq(
      Http4sPlugin.okhttp
    ),
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val servlet = libraryProject("servlet")
  .settings(
    description := "Portable servlet implementation for http4s servers",
    libraryDependencies ++= Seq(
      javaxServletApi % "provided",
      jettyServer % "test",
      jettyServlet % "test",
      mockito % "test"
    ),
  )
  .dependsOn(server % "compile;test->test")

lazy val jetty = libraryProject("jetty")
  .settings(
    description := "Jetty implementation for http4s servers",
    libraryDependencies ++= Seq(
      jettyServlet
    )
  )
  .dependsOn(servlet % "compile;test->test", theDsl % "test->test")

lazy val tomcat = libraryProject("tomcat")
  .settings(
    description := "Tomcat implementation for http4s servers",
    libraryDependencies ++= Seq(
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
  .dependsOn(core, testing % "test->test")

lazy val jawn = libraryProject("jawn")
  .settings(
    description := "Base library to parse JSON to various ASTs for http4s",
    libraryDependencies += jawnFs2
  )
  .dependsOn(core, testing % "test->test")

lazy val argonaut = libraryProject("argonaut")
  .settings(
    description := "Provides Argonaut codecs for http4s",
    libraryDependencies ++= Seq(
      Http4sPlugin.argonaut
    )
  )
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val boopickle = libraryProject("boopickle")
  .settings(
    description := "Provides Boopickle codecs for http4s",
    libraryDependencies ++= Seq(
      Http4sPlugin.boopickle
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryProject("circe")
  .settings(
    description := "Provides Circe codecs for http4s",
    libraryDependencies ++= Seq(
      circeJawn,
      circeTesting % "test"
    )
  )
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val json4s = libraryProject("json4s")
  .settings(
    description := "Base library for json4s codecs for http4s",
    libraryDependencies ++= Seq(
      jawnJson4s,
      json4sCore
    ),
  )
  .dependsOn(jawn % "compile;test->test")

lazy val json4sNative = libraryProject("json4s-native")
  .settings(
    description := "Provides json4s-native codecs for http4s",
    libraryDependencies += Http4sPlugin.json4sNative
  )
  .dependsOn(json4s % "compile;test->test")

lazy val json4sJackson = libraryProject("json4s-jackson")
  .settings(
    description := "Provides json4s-jackson codecs for http4s",
    libraryDependencies += Http4sPlugin.json4sJackson
  )
  .dependsOn(json4s % "compile;test->test")

lazy val playJson = libraryProject("play-json")
  .settings(
    description := "Provides Play json codecs for http4s",
    libraryDependencies ++= Seq(
      jawnPlay,
      Http4sPlugin.playJson
    ),
  )
  .dependsOn(jawn % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
    description := "Provides scala-xml codecs for http4s",
    libraryDependencies ++= scalaVersion(VersionNumber(_).numbers match {
      case Seq(2, scalaMajor, _*) if scalaMajor >= 11 => Seq(Http4sPlugin.scalaXml)
      case _ => Seq.empty
    }).value,
  )
  .dependsOn(core, testing % "test->test")

lazy val twirl = http4sProject("twirl")
  .settings(
    description := "Twirl template support for http4s",
    libraryDependencies += twirlApi,
    TwirlKeys.templateImports := Nil
  )
  .enablePlugins(SbtTwirl)
  .dependsOn(core, testing % "test->test")

lazy val scalatags = http4sProject("scalatags")
  .settings(
    description := "Scalatags template support for http4s",
    libraryDependencies += scalatagsApi,
  )
  .dependsOn(core, testing % "test->test")

lazy val mimedbGenerator = http4sProject("mimedb-generator")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "MimeDB source code generator",
    libraryDependencies ++= Seq(
      Http4sPlugin.treeHugger,
      Http4sPlugin.circeGeneric
    )
  )
  .dependsOn(blazeClient, circe)

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Benchmarks for http4s",
    libraryDependencies += circeParser,
  )
  .dependsOn(core, circe)

lazy val docs = http4sProject("docs")
  .enablePlugins(
    GhpagesPlugin,
    HugoPlugin,
    PrivateProjectPlugin,
    ScalaUnidocPlugin,
    TutPlugin
  )
  .settings(
    libraryDependencies ++= Seq(
      circeGeneric,
      circeLiteral,
      cryptobits
    ),
    description := "Documentation for http4s",
    autoAPIMappings := true,
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        bench,
        examples,
        examplesBlaze,
        examplesDocker,
        examplesJetty,
        examplesTomcat,
        examplesWar,
        mimedbGenerator
      ),
    Tut / scalacOptions ~= {
      val unwanted = Set("-Ywarn-unused:params", "-Ywarn-unused:imports")
      // unused params warnings are disabled due to undefined functions in the doc
      _.filterNot(unwanted) :+ "-Xfatal-warnings"
    },
    Compile / doc / scalacOptions ++= {
      scmInfo.value match {
        case Some(s) =>
          val isMaster = git.gitCurrentBranch.value == "master"
          val isSnapshot =
            git.gitCurrentTags.value.map(git.gitTagToVersionNumber.value).flatten.isEmpty
          val gitHeadCommit = git.gitHeadCommit.value
          val v = version.value
          val path =
            if (isSnapshot && isMaster)
              s"${s.browseUrl}/tree/master€{FILE_PATH}.scala"
            else if (isSnapshot)
              s"${s.browseUrl}/blob/${gitHeadCommit.get}€{FILE_PATH}.scala"
            else
              s"${s.browseUrl}/blob/v${v}€{FILE_PATH}.scala"

          Seq(
            "-implicits",
            "-doc-source-url",
            path,
            "-sourcepath",
            (ThisBuild / baseDirectory).value.getAbsolutePath
          )
        case _ => Seq.empty
      }
    },
    Compile / doc / scalacOptions -= "-Ywarn-unused:imports",
    makeSite := makeSite.dependsOn(tutQuick, http4sBuildData).value,
    Hugo / baseURL := {
      val docsPrefix = extractDocsPrefix(version.value)
      if (isTravisBuild.value) new URI(s"https://http4s.org${docsPrefix}")
      else new URI(s"http://127.0.0.1:${previewFixedPort.value.getOrElse(4000)}${docsPrefix}")
    },
    siteMappings := {
      val docsPrefix = extractDocsPrefix(version.value)
      for ((f, d) <- siteMappings.value) yield (f, docsPrefix + "/" + d)
    },
    siteMappings ++= {
      val docsPrefix = extractDocsPrefix(version.value)
      for ((f, d) <- (ScalaUnidoc / packageDoc / mappings).value)
        yield (f, s"$docsPrefix/api/$d")
    },
    ghpagesCleanSite / includeFilter := {
      new FileFilter {
        val docsPrefix = extractDocsPrefix(version.value)
        def accept(f: File) =
          f.getCanonicalPath.startsWith(
            (ghpagesRepository.value / s"${docsPrefix}").getCanonicalPath)
      }
    },
  )
  .dependsOn(client, core, theDsl, blazeServer, blazeClient, circe, dropwizardMetrics, prometheusMetrics)

lazy val website = http4sProject("website")
  .enablePlugins(HugoPlugin, GhpagesPlugin, PrivateProjectPlugin)
  .settings(
    description := "Common area of http4s.org",
    Hugo / baseURL := {
      if (isTravisBuild.value) new URI(s"https://http4s.org")
      else new URI(s"http://127.0.0.1:${previewFixedPort.value.getOrElse(4000)}")
    },
    makeSite := makeSite.dependsOn(http4sBuildData).value,
    // all .md|markdown files go into `content` dir for hugo processing
    ghpagesNoJekyll := true,
    ghpagesCleanSite / excludeFilter  :=
      new FileFilter {
        val v = ghpagesRepository.value.getCanonicalPath + "/v"
        def accept(f: File) =
          f.getCanonicalPath.startsWith(v) &&
            f.getCanonicalPath.charAt(v.size).isDigit
      }
  )

lazy val examples = http4sProject("examples")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Common code for http4s examples",
    libraryDependencies ++= Seq(
      circeGeneric,
      logbackClassic % "runtime",
      jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
    ),
    TwirlKeys.templateImports := Nil
  )
  .dependsOn(server, dropwizardMetrics, theDsl, circe, scalaXml, twirl)
  .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    fork := true,
    libraryDependencies ++= Seq(alpnBoot, dropwizardMetricsJson),
    run / javaOptions ++= addAlpnPath((Runtime / managedClasspath).value)
  )
  .dependsOn(blazeServer, blazeClient)

lazy val examplesDocker = http4sProject("examples-docker")
  .in(file("examples/docker"))
  .enablePlugins(JavaAppPackaging, DockerPlugin, PrivateProjectPlugin)
  .settings(
    description := "Builds a docker image for a blaze-server",
    Docker / packageName := "http4s/blaze-server",
    Docker / maintainer := "http4s",
    dockerUpdateLatest := true,
    dockerExposedPorts := List(8080),
  )
  .dependsOn(blazeServer, theDsl)

lazy val examplesJetty = exampleProject("examples-jetty")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Jetty",
    fork := true,
    reStart / mainClass := Some("com.example.http4s.jetty.JettyExample")
  )
  .dependsOn(jetty)

lazy val examplesTomcat = exampleProject("examples-tomcat")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Tomcat",
    fork := true,
    reStart / mainClass := Some("com.example.http4s.tomcat.TomcatExample")
  )
  .dependsOn(tomcat)

// Run this with jetty:start
lazy val examplesWar = exampleProject("examples-war")
  .enablePlugins(JettyPlugin)
  .settings(
    description := "Example of a WAR deployment of an http4s service",
    fork := true,
    libraryDependencies += javaxServletApi % "provided",
    Jetty / containerLibs := List(jettyRunner),
  )
  .dependsOn(servlet)

def http4sProject(name: String) =
  Project(name, file(name))
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      Test / testOptions += Tests.Argument(TestFrameworks.Specs2, "showtimes", "failtrace"),
      initCommands()
    )

def libraryProject(name: String) = http4sProject(name)

def exampleProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(PrivateProjectPlugin)
    .settings(libraryDependencies += logbackClassic % "runtime")
    .dependsOn(examples)

lazy val commonSettings = Seq(
  http4sJvmTarget := scalaVersion.map {
    VersionNumber(_).numbers match {
      case Seq(2, 10, _*) => "1.7"
      case _ => "1.8"
    }
  }.value,
  Compile / scalacOptions ++= Seq(
    s"-target:jvm-${http4sJvmTarget.value}"
  ),
  Compile / doc / scalacOptions += "-no-link-warnings",
  javacOptions ++= Seq(
    "-source",
    http4sJvmTarget.value,
    "-target",
    http4sJvmTarget.value,
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
  libraryDependencies ++= Seq(
    catsLaws,
    catsKernelLaws,
    discipline(scalaVersion.value),
    logbackClassic,
    scalacheck(scalaVersion.value),
    specs2Core(scalaVersion.value),
    specs2MatcherExtra(scalaVersion.value),
    specs2Scalacheck(scalaVersion.value)
  ).map(_ % "test"),
  ivyLoggingLevel := UpdateLogging.Quiet, // This doesn't seem to work? We see this in MiMa
  git.remoteRepo := "git@github.com:http4s/http4s.git",
  Hugo / includeFilter := (
    "*.html" | "*.png" | "*.jpg" | "*.gif" | "*.ico" | "*.svg" |
      "*.js" | "*.swf" | "*.json" | "*.md" |
      "*.css" | "*.woff" | "*.woff2" | "*.ttf" |
      "CNAME" | "_config.yml" | "_redirects"
  )
)

def initCommands(additionalImports: String*) =
  initialCommands := (List(
    "fs2._",
    "cats._",
    "cats.data._",
    "cats.effect._",
    "cats.implicits._"
  ) ++ additionalImports).mkString("import ", ", ", "")

// Everything is driven through release steps and the http4s* variables
// This won't actually release unless on Travis.
addCommandAlias("ci", ";clean ;release with-defaults")
