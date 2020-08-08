import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / scalaVersion := scala_213

lazy val modules: List[ProjectReference] = List(
  core,
  laws,
  testing,
  tests,
  server,
  prometheusMetrics,
  client,
  dropwizardMetrics,
  emberCore,
  emberServer,
  emberClient,
  blazeCore,
  blazeServer,
  blazeClient,
  asyncHttpClient,
  jettyClient,
  okHttpClient,
  servlet,
  jetty,
  tomcat,
  theDsl,
  jawn,
  argonaut,
  boopickle,
  circe,
  json4s,
  json4sNative,
  json4sJackson,
  playJson,
  scalaXml,
  twirl,
  scalatags,
  bench,
  examples,
  examplesBlaze,
  examplesDocker,
  examplesEmber,
  examplesJetty,
  examplesTomcat,
  examplesWar,
  scalafixInput,
  scalafixOutput,
  scalafixRules,
  scalafixTests
)

lazy val root = project.in(file("."))
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    // Root project
    name := "http4s",
    description := "A minimal, Scala-idiomatic library for HTTP",
  )
  .aggregate(modules: _*)

lazy val core = libraryProject("core")
  .enablePlugins(
    BuildInfoPlugin,
    MimeLoaderPlugin,
    SilencerPlugin
  )
  .settings(
    description := "Core http4s library for servers and clients",
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      scalaVersion,
      BuildInfoKey.map(http4sApiVersion) { case (_, v) => "apiVersion" -> v }
    ),
    buildInfoPackage := organization.value,
    libraryDependencies ++= Seq(
      cats,
      catsEffect,
      fs2Io,
      log4s,
      parboiled,
      scalaReflect(scalaVersion.value) % Provided,
      vault,
    ),
  )

lazy val laws = libraryProject("laws")
  .settings(
    description := "Instances and laws for testing http4s code",
    libraryDependencies ++= Seq(
      catsEffectLaws,
    ),
  )
  .dependsOn(core)

lazy val testing = libraryProject("testing")
  .settings(
    description := "Instances and laws for testing http4s code",
    libraryDependencies ++= Seq(
      catsEffectLaws,
      specs2Matcher,
    ),
  )
  .dependsOn(laws)

// Defined outside core/src/test so it can depend on published testing
lazy val tests = libraryProject("tests")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Tests for core project",
  )
  .dependsOn(core, testing % "test->test")

lazy val server = libraryProject("server")
  .enablePlugins(SilencerPlugin)
  .settings(
    description := "Base library for building http4s servers"
  )
  .settings(BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(BuildInfoPlugin.buildInfoDefaultSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      resourceDirectory in Test,
    ),
    buildInfoPackage := "org.http4s.server.test"
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
  .enablePlugins(SilencerPlugin)
  .settings(
    description := "Base library for building http4s clients",
    libraryDependencies += jettyServlet % Test
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

lazy val emberCore = libraryProject("ember-core")
  .settings(
    description := "Base library for ember http4s clients and servers",
    libraryDependencies ++= Seq(log4catsCore, log4catsTesting % Test),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.ember.core.Parser#Response.parser"),
    )
  )
  .dependsOn(core, testing % "test->test")

lazy val emberServer = libraryProject("ember-server")
  .settings(
    description := "ember implementation for http4s servers",
    libraryDependencies ++= Seq(log4catsSlf4j),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.ember.server.internal.ServerHelpers.server"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.ember.server.internal.ServerHelpers.server$default$12"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.ember.server.internal.ServerHelpers.server$default$12"),
    )
  )
  .dependsOn(emberCore % "compile;test->test", server % "compile;test->test")

lazy val emberClient = libraryProject("ember-client")
  .settings(
    description := "ember implementation for http4s clients",
    libraryDependencies ++= Seq(keypool, log4catsSlf4j),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.ember.core.Parser#Response.parser"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.ember.client.internal.ClientHelpers.request"),
    )
  )
  .dependsOn(emberCore % "compile;test->test", client % "compile;test->test")

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
      fs2ReactiveStreams,
    )
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val jettyClient = libraryProject("jetty-client")
  .settings(
    description := "jetty implementation for http4s clients",
    libraryDependencies ++= Seq(
      Http4sPlugin.jettyClient
    ),
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
      javaxServletApi % Provided,
      jettyServer % Test,
      jettyServlet % Test,
      mockito % Test
    ),
  )
  .dependsOn(server % "compile;test->test")

lazy val jetty = libraryProject("jetty")
  .settings(
    description := "Jetty implementation for http4s servers",
    libraryDependencies ++= Seq(
      jettyServlet,
      jettyHttp2Server
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
    )
  )
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryProject("circe")
  .settings(
    description := "Provides Circe codecs for http4s",
    libraryDependencies ++= Seq(
      circeJawn,
      circeTesting % Test
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
    libraryDependencies ++= Seq(
      Http4sPlugin.scalaXml
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val twirl = http4sProject("twirl")
  .settings(
    description := "Twirl template support for http4s",
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

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Benchmarks for http4s",
    libraryDependencies += circeParser,
    unusedCompileDependenciesFilter -= moduleFilter(organization = "org.openjdk.jmh"),
    unusedCompileDependenciesFilter -= moduleFilter(organization = "pl.project13.scala", name = "sbt-jmh-extras"),
  )
  .dependsOn(core, circe)

lazy val docs = http4sProject("docs")
  .enablePlugins(
    GhpagesPlugin,
    HugoPlugin,
    PrivateProjectPlugin,
    ScalaUnidocPlugin,
    MdocPlugin
  )
  .settings(
    crossScalaVersions := List(scala_212),
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
        scalafixInput,
        scalafixOutput,
        scalafixRules,
        scalafixTests
      ),
    Compile / scalacOptions ~= {
      val unwanted = Set("-Ywarn-unused:params", "-Xlint:missing-interpolator", "-Ywarn-unused:imports")
      // unused params warnings are disabled due to undefined functions in the doc
      _.filterNot(unwanted) :+ "-Xfatal-warnings"
    },
    mdocIn := (sourceDirectory in Compile).value / "mdoc",
    makeSite := makeSite.dependsOn(mdoc.toTask(""), http4sBuildData).value,
    Hugo / baseURL := {
      val docsPrefix = extractDocsPrefix(version.value)
      if (isCi.value) new URI(s"https://http4s.org${docsPrefix}")
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
      if (isCi.value) new URI(s"https://http4s.org")
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
      circeGeneric % Runtime,
      logbackClassic % Runtime
    ),
    TwirlKeys.templateImports := Nil
  )
  .dependsOn(server, dropwizardMetrics, theDsl, circe, scalaXml, twirl)
  .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .enablePlugins(AlpnBootPlugin)
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    fork := true,
    libraryDependencies ++= Seq(
      circeGeneric,
    ),
  )
  .dependsOn(blazeServer, blazeClient)

lazy val examplesEmber = exampleProject("examples-ember")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    fork := true
  )
  .dependsOn(emberServer, emberClient)

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
    libraryDependencies += javaxServletApi % Provided,
    Jetty / containerLibs := List(jettyRunner),
  )
  .dependsOn(servlet)

lazy val scalafixSettings: Seq[Setting[_]] = Seq(
  developers ++= List(
    Developer(
      "amarrella",
      "Alessandro Marrella",
      "hello@alessandromarrella.com",
      url("https://alessandromarrella.com")
    ),
    Developer(
      "satorg",
      "Sergey Torgashov",
      "satorg@gmail.com",
      url("https://github.com/satorg")
    ),
  ),
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions += "-Yrangepos",
  mimaPreviousArtifacts := Set.empty,
)

lazy val scalafixRules = project
  .in(file("scalafix/rules"))
  .settings(scalafixSettings)
  .settings(
    moduleName := "http4s-scalafix",
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafix,
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val scalafixInput = project
  .in(file("scalafix/input"))
  .settings(scalafixSettings)
  .settings(
    skip in publish := true,
    libraryDependencies ++= List(
      "http4s-blaze-client",
      "http4s-blaze-server",
      "http4s-dsl",
    ).map("org.http4s" %% _ % "0.21.0"),
    // TODO: I think these are false positives
    unusedCompileDependenciesFilter -= moduleFilter(organization = "org.http4s"),
    scalacOptions -= "-Xfatal-warnings",
  )
  // Syntax matters as much as semantics here.
  .disablePlugins(HeaderPlugin, ScalafmtPlugin)

lazy val scalafixOutput = project
  .in(file("scalafix/output"))
  .settings(scalafixSettings)
  .settings(
    skip in publish := true,
    skip in compile := true,
    Compile / doc / sources := Nil
  )
  // Auto-formatting prevents the tests from passing
  .disablePlugins(HeaderPlugin, ScalafmtPlugin)

lazy val scalafixTests = project
  .in(file("scalafix/tests"))
  .settings(commonSettings)
  .settings(scalafixSettings)
  .settings(
    skip in publish := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafix % Test cross CrossVersion.full,
    Compile / compile :=
      (Compile / compile).dependsOn(scalafixInput / Compile / compile).value,
    scalafixTestkitOutputSourceDirectories :=
      (scalafixOutput / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (scalafixInput / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (scalafixInput / Compile / fullClasspath).value,
  )
  .dependsOn(scalafixRules)
  .enablePlugins(ScalafixTestkitPlugin)
  .enablePlugins(AutomateHeaderPlugin)

def http4sProject(name: String) =
  Project(name, file(name))
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      Test / testOptions += Tests.Argument(TestFrameworks.Specs2, "showtimes", "failtrace"),
      initCommands()
    )
    .enablePlugins(AutomateHeaderPlugin)

def libraryProject(name: String) = http4sProject(name)

def exampleProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(PrivateProjectPlugin)
    .settings(libraryDependencies += logbackClassic % Runtime)
    .dependsOn(examples)

lazy val commonSettings = Seq(
  Compile / doc / scalacOptions += "-no-link-warnings",
  javacOptions ++= Seq(
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
  libraryDependencies ++= Seq(
    catsEffectTestingSpecs2,
    catsLaws,
    catsKernelLaws,
    disciplineSpecs2,
    logbackClassic,
    scalacheck,
    specs2Cats,
    specs2Core,
    specs2MatcherExtra,
    specs2Scalacheck
  ).map(_ % Test),
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
