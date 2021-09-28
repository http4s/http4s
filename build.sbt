import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._
import org.http4s.sbt.ScaladocApiMapping
import sbtcrossproject.{CrossProject, CrossType, Platform}
import java.util.concurrent.TimeUnit
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / crossScalaVersions := Seq(scala_213, scala_212, scala_3)
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.filter(_.startsWith("2.")).last
ThisBuild / baseVersion := "1.0"
ThisBuild / publishGithubUser := "rossabaker"
ThisBuild / publishFullName := "Ross A. Baker"

ThisBuild / githubWorkflowUseSbtThinClient := false
ThisBuild / githubWorkflowBuildPreamble +=
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v2.4.0"),
    name = Some("Setup NodeJS v16"),
    params = Map("node-version" -> "16"),
    cond = Some("matrix.ci == 'ciNodeJS'"))
ThisBuild / githubWorkflowBuild := Seq(
  // todo remove once salafmt properly supports scala3
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "scalafmtCheckAll"),
    name = Some("Check formatting"),
    cond = Some(s"matrix.scala != '$scala_3'")),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "headerCheck", "test:headerCheck"),
    name = Some("Check headers")),
  WorkflowStep.Sbt(List("${{ matrix.ci }}", "test:compile"), name = Some("Compile")),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "fastOptJS", "test:fastOptJS"),
    name = Some("FastOptJS"),
    cond = Some("matrix.ci != 'ciJVM'")),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "mimaReportBinaryIssues"),
    name = Some("Check binary compatibility"),
    cond = Some("matrix.ci == 'ciJVM'")),
  // TODO: this gives false positives for boopickle, scalatags, twirl and play-json
  // WorkflowStep.Sbt(
  // List("unusedCompileDependenciesTest"),
  // name = Some("Check unused compile dependencies"), cond = Some(s"matrix.scala != '$scala_3'")), // todo disable on dotty for now
  WorkflowStep.Sbt(List("${{ matrix.ci }}", "test"), name = Some("Run tests")),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "doc"),
    name = Some("Build docs"),
    cond = Some("matrix.ci == 'ciJVM'"))
)

val ciVariants = List("ciJVM", "ciNodeJS")
val jsCiVariants = ciVariants.tail
ThisBuild / githubWorkflowBuildMatrixAdditions += "ci" -> ciVariants

val ScalaJSJava = "adopt@1.8"
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  jsCiVariants.flatMap { ci =>
    val javaFilters =
      (ThisBuild / githubWorkflowJavaVersions).value.filterNot(Set(ScalaJSJava)).map { java =>
        MatrixExclude(Map("ci" -> ci, "java" -> java))
      }

    javaFilters
  }
}

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
    scalas = crossScalaVersions.value.toList,
    javas = List("adoptium@8"),
  ))

addCommandAlias("ciJVM", "; project rootJVM")
addCommandAlias("ciNodeJS", "; set parallelExecution := false; project rootJS")

enablePlugins(SonatypeCiReleasePlugin)

versionIntroduced.withRank(KeyRanks.Invisible) := Map(
  scala_3 -> "0.22.0"
)

lazy val crossModules: List[CrossProject] = List(
  core,
  laws,
  testing,
  tests,
  server,
  serverTesting,
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
  nodeServerless,
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

lazy val modules: List[ProjectReference] =
  crossModules.flatMap(_.componentProjects).map(x => x: ProjectReference)

lazy val jsModules: List[ProjectReference] =
  crossModules.flatMap(_.projects.get(JSPlatform)).map(x => x: ProjectReference)

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(
    // Root project
    name := "http4s",
    description := "A minimal, Scala-idiomatic library for HTTP",
    startYear := Some(2013)
  )
  .aggregate(modules: _*)

lazy val rootJVM = project
  .enablePlugins(NoPublishPlugin)
  .aggregate(crossModules.flatMap(_.projects.get(JVMPlatform)).map(x => x: ProjectReference): _*)

lazy val rootJS = project
  .enablePlugins(NoPublishPlugin)
  .aggregate(jsModules: _*)

lazy val core = libraryProject("core", CrossType.Full, List(JVMPlatform, JSPlatform))
  .enablePlugins(
    BuildInfoPlugin,
    MimeLoaderPlugin
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
      caseInsensitive.value,
      catsCore.value,
      catsEffectStd.value,
      catsParse.value.exclude("org.typelevel", "cats-core_2.13"),
      crypto.value,
      fs2Io.value,
      ip4sCore.value,
      literally.value,
      log4s.value,
      munit.value % Test,
      scodecBits.value,
      slf4jApi, // residual dependency from macros
      vault.value
    ),
    libraryDependencies ++= {
      if (isDotty.value) Seq.empty
      else
        Seq(
          scalaReflect(scalaVersion.value) % Provided
        )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang", "scala-reflect")
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalaJavaLocalesEnUS.value,
      scalaJavaTime.value
    ),
    scalacOptions ~= { _.filterNot(_ == "-Xfatal-warnings") }
  )
  .jsConfigure(_.disablePlugins(DoctestPlugin))

lazy val laws = libraryProject("laws", CrossType.Pure, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Instances and laws for testing http4s code",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      caseInsensitiveTesting.value,
      catsEffect.value,
      catsEffectTestkit.value,
      catsLaws.value,
      disciplineCore.value,
      scalacheck.value,
      scalacheckEffectMunit.value,
      munitCatsEffect.value
    ),
    unusedCompileDependenciesFilter -= moduleFilter(
      organization = "org.typelevel",
      name = "scalacheck-effect-munit"),
  )
  .dependsOn(core)

lazy val testing = libraryProject("testing", CrossType.Full, List(JVMPlatform, JSPlatform))
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Internal utilities for http4s tests",
    startYear := Some(2016),
    libraryDependencies ++= Seq(
      catsEffectLaws.value,
      munit.value,
      munitCatsEffect.value,
      munitDiscipline.value,
      scalacheck.value,
      scalacheckEffect.value,
      scalacheckEffectMunit.value
    ).map(_ % Test)
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalaJavaTimeTzdb.value
    ).map(_ % Test)
  )
  .dependsOn(laws)

// Defined outside core/src/test so it can depend on published testing
lazy val tests = libraryProject("tests", CrossType.Full, List(JVMPlatform, JSPlatform))
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Tests for core project",
    startYear := Some(2013)
  )
  .jsConfigure(_.disablePlugins(DoctestPlugin))
  .dependsOn(core, testing % "test->test")

lazy val server = libraryProject("server", CrossType.Full, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Base library for building http4s servers",
    startYear := Some(2014)
  )
  .settings(BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(BuildInfoPlugin.buildInfoDefaultSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](Test / resourceDirectory),
    buildInfoPackage := "org.http4s.server.test"
  )
  .jsSettings(Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)))
  .dependsOn(core, testing % "test->test", theDsl % "test->compile")

// Defined outside server to avoid circular dependency with client
lazy val serverTesting =
  libraryProject("server-testing", CrossType.Pure, List(JVMPlatform, JSPlatform))
    .enablePlugins(NoPublishPlugin)
    .settings(
      description := "Tests for server project",
      startYear := Some(2021)
    )
    .jsConfigure(_.disablePlugins(DoctestPlugin))
    .dependsOn(server, testing % "test->test", client % "test->test")

lazy val prometheusMetrics = libraryProject("prometheus-metrics")
  .settings(
    description := "Support for Prometheus Metrics",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      prometheusClient,
      prometheusCommon,
      prometheusHotspot
    )
  )
  .dependsOn(
    core % "compile->compile",
    theDsl % "test->compile",
    testing % "test->test",
    server % "test->compile",
    client % "test->compile"
  )

lazy val client = libraryProject("client", CrossType.Full, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Base library for building http4s clients",
    startYear := Some(2014),
    libraryDependencies += munit.value % Test
  )
  .dependsOn(core, testing % "test->test", server % "test->compile", theDsl % "test->compile")
  .jsConfigure(_.dependsOn(nodeServerless.js % Test))

lazy val dropwizardMetrics = libraryProject("dropwizard-metrics")
  .settings(
    description := "Support for Dropwizard Metrics",
    startYear := Some(2018),
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

lazy val emberCore = libraryProject("ember-core", CrossType.Pure, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Base library for ember http4s clients and servers",
    startYear := Some(2019),
    unusedCompileDependenciesFilter -= moduleFilter("io.chrisdavenport", "log4cats-core"),
    libraryDependencies ++= Seq(
      fs2Io.value,
      log4catsTesting.value % Test
    )
  )
  .jsSettings(
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )
  .dependsOn(core, testing % "test->test")

lazy val emberServer = libraryProject("ember-server", CrossType.Full, List(JVMPlatform, JSPlatform))
  .settings(
    description := "ember implementation for http4s servers",
    startYear := Some(2019)
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      log4catsSlf4j.value,
      javaWebSocket % Test,
      jnrUnixSocket % Test, // Necessary for jdk < 16
    ),
    Test / parallelExecution := false
  )
  .jsSettings(
    libraryDependencies += log4catsNoop.value,
    Test / npmDependencies += "ws" -> "8.2.2",
    useYarn := true,
    yarnExtraArgs += "--frozen-lockfile",
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )
  .dependsOn(
    emberCore % "compile;test->test",
    server % "compile;test->test",
    serverTesting % "test->test",
    emberClient % "test->compile")
  .jsEnablePlugins(ScalaJSBundlerPlugin)

lazy val emberClient = libraryProject("ember-client", CrossType.Full, List(JVMPlatform, JSPlatform))
  .settings(
    description := "ember implementation for http4s clients",
    startYear := Some(2019),
    libraryDependencies += keypool.value,
  )
  .jvmSettings(libraryDependencies += log4catsSlf4j.value)
  .jsSettings(
    libraryDependencies += log4catsNoop.value,
    scalacOptions ~= { _.filterNot(_ == "-Xfatal-warnings") },
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )
  .dependsOn(emberCore % "compile;test->test", client % "compile;test->test")

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
    description := "Base library for binding blaze to http4s clients and servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      blazeHttp.value
    )
  )
  .dependsOn(core, testing % "test->test")

lazy val blazeServer = libraryProject("blaze-server")
  .settings(
    description := "blaze implementation for http4s servers",
    startYear := Some(2014),
  )
  .dependsOn(blazeCore % "compile;test->test", server % "compile;test->test")

lazy val blazeClient = libraryProject("blaze-client")
  .settings(
    description := "blaze implementation for http4s clients",
    startYear := Some(2014),
  )
  .dependsOn(blazeCore % "compile;test->test", client % "compile;test->test")

lazy val asyncHttpClient = libraryProject("async-http-client")
  .settings(
    description := "async http client implementation for http4s clients",
    startYear := Some(2016),
    libraryDependencies ++= Seq(
      Http4sPlugin.asyncHttpClient,
      fs2ReactiveStreams.value,
      nettyBuffer,
      nettyCodecHttp,
      reactiveStreams
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
      jettyUtil
    )
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val nodeServerless = libraryProject("node-serverless", CrossType.Pure, List(JSPlatform))
  .settings(
    description := "Node.js serverless wrapper for http4s apps",
    startYear := Some(2021),
    libraryDependencies ++= Seq(
      fs2Io.value
    ),
    scalacOptions ~= { _.filterNot(_ == "-Xfatal-warnings") },
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )
  .dependsOn(core)

lazy val okHttpClient = libraryProject("okhttp-client")
  .settings(
    description := "okhttp implementation for http4s clients",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.okhttp,
      okio
    )
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
    )
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
      jettyUtil
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
      tomcatUtilScan
    )
  )
  .dependsOn(servlet % "compile;test->test")

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryProject("dsl", CrossType.Pure, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Simple DSL for writing http4s services",
    startYear := Some(2013),
    libraryDependencies += munit.value % Test
  )
  .jsConfigure(_.disablePlugins(DoctestPlugin))
  .dependsOn(core, testing % "test->test")

lazy val jawn = libraryProject("jawn", CrossType.Pure, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Base library to parse JSON to various ASTs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      jawnFs2.value,
      jawnParser.value
    )
  )
  .dependsOn(core, testing % "test->test")

lazy val boopickle = libraryProject("boopickle", CrossType.Pure, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Provides Boopickle codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.boopickle.value,
      munit.value % Test
    )
  )
  .jsConfigure(_.disablePlugins(DoctestPlugin))
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryProject("circe", CrossType.Pure, List(JVMPlatform, JSPlatform))
  .settings(
    description := "Provides Circe codecs for http4s",
    startYear := Some(2015),
    libraryDependencies ++= Seq(
      circeCore.value,
      circeJawn.value,
      circeTesting.value % Test,
      munit.value % Test
    )
  )
  .dependsOn(core, jawn % "compile;test->test", testing % "test->test")

lazy val playJson = libraryProject("play-json")
  .settings(
    description := "Provides Play json codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.playJson.value.cross(CrossVersion.for3Use2_13)
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
      Http4sPlugin.scalaXml.value
    )
  )
  .dependsOn(core, testing % "test->test")

// Full cross helps workaround issues with twirl directories
lazy val twirl = http4sProject("twirl", CrossType.Full, List(JVMPlatform))
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
      scalatagsApi.value.cross(CrossVersion.for3Use2_13)
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
    libraryDependencies += circeParser.value,
    undeclaredCompileDependenciesTest := {},
    unusedCompileDependenciesTest := {}
  )
  .dependsOn(core, circe, emberCore)

// Workaround via full cross
lazy val docs = http4sProject("docs", CrossType.Full, List(JVMPlatform))
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
      circeGeneric.value,
      circeLiteral.value,
      cryptobits.value
    ),
    description := "Documentation for http4s",
    startYear := Some(2013),
    autoAPIMappings := true,
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        (List[ProjectReference](
          bench.jvm,
          examples.jvm,
          examplesBlaze.jvm,
          examplesDocker.jvm,
          examplesJetty.jvm,
          examplesTomcat.jvm,
          examplesWar.jvm) ++ jsModules): _*),
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
        (ScalaUnidoc / unidoc / unidocAllClasspaths).value,
        scalaBinaryVersion.value
      )
    }
  )
  .dependsOn(
    client,
    core,
    theDsl,
    blazeServer,
    blazeClient,
    circe,
    dropwizardMetrics,
    prometheusMetrics)

// Workaround via full cross
lazy val website = http4sProject("website", CrossType.Full, List(JVMPlatform))
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
    ghpagesCleanSite / excludeFilter :=
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
      circeGeneric.value % Runtime,
      logbackClassic % Runtime
    )
    // todo enable when twirl supports dotty TwirlKeys.templateImports := Nil,
  )
  .dependsOn(server, dropwizardMetrics, theDsl, circe, scalaXml /*, twirl*/ )
// todo enable when twirl supports dotty .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    startYear := Some(2013),
    fork := true,
    libraryDependencies ++= Seq(
      circeGeneric.value
    )
  )
  .dependsOn(blazeServer, blazeClient)

lazy val examplesEmber = exampleProject("examples-ember")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    startYear := Some(2020),
    fork := true
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
    dockerExposedPorts := List(8080)
  )
  .dependsOn(blazeServer, theDsl)

lazy val examplesJetty = exampleProject("examples-jetty")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Jetty",
    startYear := Some(2014),
    fork := true,
    reStart / mainClass := Some("com.example.http4s.jetty.JettyExample")
  )
  .dependsOn(jettyServer)

lazy val examplesTomcat = exampleProject("examples-tomcat")
  .settings(Revolver.settings)
  .settings(
    description := "Example of http4s server on Tomcat",
    startYear := Some(2014),
    fork := true,
    reStart / mainClass := Some("com.example.http4s.tomcat.TomcatExample")
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
    Jetty / containerLibs := List(jettyRunner)
  )
  .dependsOn(servlet)

def http4sProject(
    name: String,
    crossType: CrossType = CrossType.Pure,
    platforms: Seq[Platform] = List(JVMPlatform)) =
  CrossProject(name, file(name))(platforms: _*)
    .withoutSuffixFor(JVMPlatform)
    .crossType(crossType)
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      testFrameworks += new TestFramework("munit.Framework"),
      Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b"),
      initCommands()
    )
    .enablePlugins(Http4sPlugin)

def libraryProject(
    name: String,
    crossType: CrossType = CrossType.Pure,
    platforms: Seq[Platform] = List(JVMPlatform)) = http4sProject(name, crossType, platforms)

def exampleProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(NoPublishPlugin)
    .settings(libraryDependencies += logbackClassic % Runtime)
    .dependsOn(examples)

lazy val commonSettings = Seq(
  Compile / doc / scalacOptions += "-no-link-warnings",
  libraryDependencies ++= Seq(
    catsLaws.value,
    logbackClassic,
    scalacheck.value
  ).map(_ % Test),
  apiURL := Some(url(s"https://http4s.org/v${baseVersion.value}/api"))
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
