import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._
import org.http4s.sbt.ScaladocApiMapping
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / crossScalaVersions := Seq(scala_213, scala_212, scala_3)
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.filter(_.startsWith("2.")).last
ThisBuild / baseVersion := "1.0"
ThisBuild / publishGithubUser := "rossabaker"
ThisBuild / publishFullName   := "Ross A. Baker"

ThisBuild / githubWorkflowBuild := Seq(
      // todo remove once salafmt properly supports scala3
      WorkflowStep.Sbt(List("scalafmtCheckAll"), name = Some("Check formatting"), cond = Some(s"matrix.scala != '$scala_3'")),
      WorkflowStep.Sbt(List("headerCheck", "test:headerCheck"), name = Some("Check headers")),
      WorkflowStep.Sbt(List("test:compile"), name = Some("Compile")),
      WorkflowStep.Sbt(List("mimaReportBinaryIssues"), name = Some("Check binary compatibility")),
      // TODO: this gives false positives for boopickle, scalatags, twirl and play-json
      // WorkflowStep.Sbt(
        // List("unusedCompileDependenciesTest"),
        // name = Some("Check unused compile dependencies"), cond = Some(s"matrix.scala != '$scala_3'")), // todo disable on dotty for now
      WorkflowStep.Sbt(List("test"), name = Some("Run tests")),
      WorkflowStep.Sbt(List("doc"), name = Some("Build docs"))
    )

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    "scalafix",
    "Scalafix",
    githubWorkflowJobSetup.value.toList ::: List(
      WorkflowStep.Run(
        List("cd scalafix", "sbt ci"),
        name = Some("Scalafix tests"),
        cond = Some(s"matrix.scala == '$scala_213'")
      )
    ),
    scalas = crossScalaVersions.value.toList
  ))


enablePlugins(SonatypeCiReleasePlugin)

versionIntroduced.withRank(KeyRanks.Invisible) := Map(
  scala_3 -> "0.22.0",
)

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
  jettyServer,
  jettyClient,
  okHttpClient,
  servlet,
  tomcatServer,
  theDsl,
  jawn,
  boopickle,
  circe,
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
  examplesWar
)

lazy val root = project.in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(
    // Root project
    name := "http4s",
    description := "A minimal, Scala-idiomatic library for HTTP",
    startYear := Some(2013),
  )
  .aggregate(modules: _*)

lazy val core = libraryProject("core")
  .enablePlugins(
    BuildInfoPlugin,
    MimeLoaderPlugin,
  )
  .settings(
    description := "Core http4s library for servers and clients",
    startYear := Some(2013),
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      scalaVersion,
      BuildInfoKey.map(http4sApiVersion) { case (_, v) => "apiVersion" -> v }
    ),
    buildInfoPackage := organization.value,
    libraryDependencies ++= Seq(
      caseInsensitive,
      catsCore,
      catsEffectStd,
      catsParse.exclude("org.typelevel", "cats-core_2.13"),
      fs2Core,
      fs2Io,
      ip4sCore,
      literally,
      log4s,
      scodecBits,
      slf4jApi, // residual dependency from macros
      vault,
    ),
    libraryDependencies ++= {
      if (isDotty.value) Seq.empty
      else Seq(
        scalaReflect(scalaVersion.value) % Provided
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang", "scala-reflect"),
  )

lazy val laws = libraryProject("laws")
  .settings(
    description := "Instances and laws for testing http4s code",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      caseInsensitiveTesting,
      catsEffect,
      catsEffectTestkit,
      catsLaws,
      disciplineCore,
      ip4sTestKit,
      scalacheck,
      scalacheckEffectMunit,
      munitCatsEffect
    ),
    unusedCompileDependenciesFilter -= moduleFilter(organization = "org.typelevel", name = "scalacheck-effect-munit"),
  )
  .dependsOn(core)

lazy val testing = libraryProject("testing")
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Internal utilities for http4s tests",
    startYear := Some(2016),
    libraryDependencies ++= Seq(
      catsEffectLaws,
      munitCatsEffect,
      munitDiscipline,
      scalacheck,
      scalacheckEffect,
      scalacheckEffectMunit,
    ).map(_ % Test),
  )
  .dependsOn(laws)

// Defined outside core/src/test so it can depend on published testing
lazy val tests = libraryProject("tests")
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Tests for core project",
    startYear := Some(2013),
  )
  .dependsOn(core, testing % "test->test")

lazy val server = libraryProject("server")
  .settings(
    description := "Base library for building http4s servers",
    startYear := Some(2014),
  )
  .settings(BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(BuildInfoPlugin.buildInfoDefaultSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](Test / resourceDirectory),
    buildInfoPackage := "org.http4s.server.test",
  )
  .dependsOn(core, testing % "test->test", theDsl % "test->compile")

lazy val prometheusMetrics = libraryProject("prometheus-metrics")
  .settings(
    description := "Support for Prometheus Metrics",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      prometheusClient,
      prometheusCommon,
      prometheusHotspot,
    ),
  )
  .dependsOn(
    core % "compile->compile",
    theDsl % "test->compile",
    testing % "test->test",
    server % "test->compile",
    client % "test->compile"
  )

lazy val client = libraryProject("client")
  .settings(
    description := "Base library for building http4s clients",
    startYear := Some(2014),
  )
  .dependsOn(
    core,
    testing % "test->test",
    server % "test->compile",
    theDsl % "test->compile")

lazy val dropwizardMetrics = libraryProject("dropwizard-metrics")
  .settings(
    description := "Support for Dropwizard Metrics",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      dropwizardMetricsCore,
      dropwizardMetricsJson,
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
    startYear := Some(2019),
    unusedCompileDependenciesFilter -= moduleFilter("io.chrisdavenport", "log4cats-core"),
    libraryDependencies ++= Seq(
      log4catsTesting % Test,
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val emberServer = libraryProject("ember-server")
  .settings(
    description := "ember implementation for http4s servers",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      log4catsSlf4j,
    ),
  )
  .dependsOn(emberCore % "compile;test->test", server % "compile;test->test", emberClient % "test->compile")

lazy val emberClient = libraryProject("ember-client")
  .settings(
    description := "ember implementation for http4s clients",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      keypool,
      log4catsSlf4j,
    ),
  )
  .dependsOn(emberCore % "compile;test->test", client % "compile;test->test")

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
    description := "Base library for binding blaze to http4s clients and servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      blazeHttp,
    )
  )
  .dependsOn(core, testing % "test->test")

lazy val blazeServer = libraryProject("blaze-server")
  .settings(
    description := "blaze implementation for http4s servers",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      // privat constructor with new parameter
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.server.blaze.BlazeServerBuilder.this")
    )
  )
  .dependsOn(blazeCore % "compile;test->test", server % "compile;test->test")

lazy val blazeClient = libraryProject("blaze-client")
  .settings(
    description := "blaze implementation for http4s clients",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.canEqual"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.productArity"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.productElement"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.productElementName"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.productElementNames"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.productIterator"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.productPrefix"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection.reset"),
      ProblemFilters.exclude[FinalMethodProblem]("org.http4s.client.blaze.Http1Connection#Idle.toString"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.client.blaze.Http1Connection$Running$"),
      ProblemFilters.exclude[MissingTypesProblem]("org.http4s.client.blaze.Http1Connection$Idle$"),
    ),
  )
  .dependsOn(blazeCore % "compile;test->test", client % "compile;test->test")

lazy val asyncHttpClient = libraryProject("async-http-client")
  .settings(
    description := "async http client implementation for http4s clients",
    startYear := Some(2016),
    libraryDependencies ++= Seq(
      Http4sPlugin.asyncHttpClient,
      fs2ReactiveStreams,
      nettyBuffer,
      nettyCodecHttp,
      reactiveStreams,
    ),
    Test / parallelExecution := false
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val jettyClient = libraryProject("jetty-client")
  .settings(
    description := "jetty implementation for http4s clients",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.jettyClient,
      jettyHttp,
      jettyUtil,
    ),
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val okHttpClient = libraryProject("okhttp-client")
  .settings(
    description := "okhttp implementation for http4s clients",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.okhttp,
      okio,
    ),
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val servlet = libraryProject("servlet")
  .settings(
    description := "Portable servlet implementation for http4s servers",
    startYear := Some(2013),
    libraryDependencies ++= Seq(
      javaxServletApi % Provided,
      Http4sPlugin.jettyServer % Test,
      jettyServlet % Test,
      Http4sPlugin.asyncHttpClient % Test
    ),
  )
  .dependsOn(server % "compile;test->test")

lazy val jettyServer = libraryProject("jetty-server")
  .settings(
    description := "Jetty implementation for http4s servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      jettyHttp2Server,
      Http4sPlugin.jettyServer,
      jettyServlet,
      jettyUtil,
    )
  )
  .dependsOn(servlet % "compile;test->test", theDsl % "test->test")

lazy val tomcatServer = libraryProject("tomcat-server")
  .settings(
    description := "Tomcat implementation for http4s servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      tomcatCatalina,
      tomcatCoyote,
      tomcatUtilScan,
    )
  )
  .dependsOn(servlet % "compile;test->test")

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryProject("dsl")
  .settings(
    description := "Simple DSL for writing http4s services",
    startYear := Some(2013),
  )
  .dependsOn(core, testing % "test->test")

lazy val jawn = libraryProject("jawn")
  .settings(
    description := "Base library to parse JSON to various ASTs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      jawnFs2,
      jawnParser,
    )
  )
  .dependsOn(core, testing % "test->test")

lazy val boopickle = libraryProject("boopickle")
  .settings(
    description := "Provides Boopickle codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.boopickle.cross(CrossVersion.for3Use2_13)
    ),
    compile / skip := isDotty.value,
    publish / skip := isDotty.value
  )
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryProject("circe")
  .settings(
    description := "Provides Circe codecs for http4s",
    startYear := Some(2015),
    libraryDependencies ++= Seq(
      circeCore,
      circeJawn,
      circeTesting % Test
    )
  )
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val playJson = libraryProject("play-json")
  .settings(
    description := "Provides Play json codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.playJson.cross(CrossVersion.for3Use2_13)
    ),
    publish / skip := isDotty.value,
    compile / skip := isDotty.value
  )
  .dependsOn(jawn % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
    description := "Provides scala-xml codecs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      Http4sPlugin.scalaXml,
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val twirl = http4sProject("twirl")
  .settings(
    description := "Twirl template support for http4s",
    startYear := Some(2014),
    TwirlKeys.templateImports := Nil,
    libraryDependencies := {
      libraryDependencies.value.map {
        case module if module.name == "twirl-api" =>
          module.cross(CrossVersion.for3Use2_13)
        case module => module
      }
    },
    publish / skip := isDotty.value
  )
  .enablePlugins(SbtTwirl)
  .dependsOn(core, testing % "test->test")

lazy val scalatags = http4sProject("scalatags")
  .settings(
    description := "Scalatags template support for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      scalatagsApi.cross(CrossVersion.for3Use2_13)
    ),
    publish / skip := isDotty.value
  )
  .dependsOn(core, testing % "test->test")

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Benchmarks for http4s",
    startYear := Some(2015),
    libraryDependencies += circeParser,
    undeclaredCompileDependenciesTest := {},
    unusedCompileDependenciesTest := {},
  )
  .dependsOn(core, circe)

lazy val docs = http4sProject("docs")
  .enablePlugins(
    GhpagesPlugin,
    HugoPlugin,
    NoPublishPlugin,
    ScalaUnidocPlugin,
    MdocPlugin
  )
  .settings(docsProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      circeGeneric,
      circeLiteral,
      cryptobits
    ),
    description := "Documentation for http4s",
    startYear := Some(2013),
    autoAPIMappings := true,
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        bench,
        examples,
        examplesBlaze,
        examplesDocker,
        examplesJetty,
        examplesTomcat,
        examplesWar
      ),
    mdocIn := (Compile / sourceDirectory).value / "mdoc",
    makeSite := makeSite.dependsOn(mdoc.toTask(""), http4sBuildData).value,
    fatalWarningsInCI := false,
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
    apiMappings ++= {
      ScaladocApiMapping.mappings(
        (ScalaUnidoc / unidoc / unidocAllClasspaths).value, scalaBinaryVersion.value
      )
    }
  )
  .dependsOn(client, core, theDsl, blazeServer, blazeClient, circe, dropwizardMetrics, prometheusMetrics)

lazy val website = http4sProject("website")
  .enablePlugins(HugoPlugin, GhpagesPlugin, NoPublishPlugin)
  .settings(docsProjectSettings)
  .settings(
    description := "Common area of http4s.org",
    startYear := Some(2013),
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
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Common code for http4s examples",
    startYear := Some(2013),
    libraryDependencies ++= Seq(
      circeGeneric % Runtime,
      logbackClassic % Runtime
    ),
    // todo enable when twirl supports dotty TwirlKeys.templateImports := Nil,
  )
  .dependsOn(server, dropwizardMetrics, theDsl, circe, scalaXml/*, twirl*/)
  // todo enable when twirl supports dotty .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    startYear := Some(2013),
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
    startYear := Some(2020),
    fork := true,
  )
  .dependsOn(emberServer, emberClient)

lazy val examplesDocker = http4sProject("examples-docker")
  .in(file("examples/docker"))
  .enablePlugins(JavaAppPackaging, DockerPlugin, NoPublishPlugin)
  .settings(
    description := "Builds a docker image for a blaze-server",
    startYear := Some(2017),
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
    startYear := Some(2014),
    fork := true,
    reStart / mainClass := Some("com.example.http4s.jetty.JettyExample"),
  )
  .dependsOn(jettyServer)

lazy val examplesTomcat = exampleProject("examples-tomcat")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Tomcat",
    startYear := Some(2014),
    fork := true,
    reStart / mainClass := Some("com.example.http4s.tomcat.TomcatExample"),
  )
  .dependsOn(tomcatServer)

// Run this with jetty:start
lazy val examplesWar = exampleProject("examples-war")
  .enablePlugins(JettyPlugin)
  .settings(
    description := "Example of a WAR deployment of an http4s service",
    startYear := Some(2014),
    fork := true,
    libraryDependencies += javaxServletApi % Provided,
    Jetty / containerLibs := List(jettyRunner),
  )
  .dependsOn(servlet)

def http4sProject(name: String) =
  Project(name, file(name))
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      testFrameworks += new TestFramework("munit.Framework"),
      initCommands()
    )
    .enablePlugins(Http4sPlugin)

def libraryProject(name: String) = http4sProject(name)

def exampleProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(NoPublishPlugin)
    .settings(libraryDependencies += logbackClassic % Runtime)
    .dependsOn(examples)

lazy val commonSettings = Seq(
  Compile / doc / scalacOptions += "-no-link-warnings",
  libraryDependencies ++= Seq(
    catsLaws,
    logbackClassic,
    scalacheck,
  ).map(_ % Test),
  apiURL := Some(url(s"https://http4s.org/v${baseVersion.value}/api")),
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
