import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._
import org.http4s.sbt.{ScaladocApiMapping, SiteConfig}

import scala.xml.transform.{RewriteRule, RuleTransformer}

Global / excludeLintKeys += laikaDescribe

// Global settings
ThisBuild / crossScalaVersions := Seq(scala_213, scala_212, scala_3)
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.filter(_.startsWith("2.")).last
ThisBuild / baseVersion := "0.23"
ThisBuild / publishGithubUser := "rossabaker"
ThisBuild / publishFullName := "Ross A. Baker"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbOptions ++= Seq("-P:semanticdb:synthetics:on").filter(_ => !isDotty.value)
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"

ThisBuild / scalafixAll / skip := isDotty.value
ThisBuild / ScalafixConfig / skip := isDotty.value

ThisBuild / githubWorkflowBuild := Seq(
  // todo remove once salafmt properly supports scala3
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "scalafmtCheckAll", "scalafmtSbtCheck"),
    name = Some("Check formatting"),
  ),
  WorkflowStep
    .Sbt(List("${{ matrix.ci }}", "headerCheck", "test:headerCheck"), name = Some("Check headers")),
  WorkflowStep.Sbt(List("${{ matrix.ci }}", "test:compile"), name = Some("Compile")),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "scalafixAll --check"),
    name = Some("Check Scalafix rules"),
    cond = Some(s"matrix.scala != '$scala_3'"),
  ),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "mimaReportBinaryIssues"),
    name = Some("Check binary compatibility"),
  ),
  // TODO: this gives false positives for boopickle, scalatags, twirl and play-json
  // WorkflowStep.Sbt(
  // List("${{ matrix.ci }}", "unusedCompileDependenciesTest"),
  // name = Some("Check unused compile dependencies"), cond = Some(s"matrix.scala != '$scala_3'")), // todo disable on dotty for now
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "fastOptJS", "test:fastOptJS"),
    name = Some("FastOptJS"),
    cond = Some("matrix.ci != 'ciJVM'"),
  ),
  WorkflowStep.Sbt(List("${{ matrix.ci }}", "test"), name = Some("Run tests")),
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "doc"),
    name = Some("Build docs"),
    cond = Some("matrix.ci == 'ciJVM'"),
  ),
)

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    "scalafix",
    "Scalafix",
    githubWorkflowJobSetup.value.toList ::: List(
      WorkflowStep.Run(
        List("cd scalafix", "sbt ci"),
        name = Some("Scalafix tests"),
        cond = Some(s"matrix.scala == '$scala_213'"),
      )
    ),
    scalas = crossScalaVersions.value.toList,
    javas = List(JavaSpec.temurin("8")),
  )
)

val ciVariants = List("ciJVM", "ciNodeJS")
ThisBuild / githubWorkflowBuildMatrixAdditions += "ci" -> ciVariants

// Only one Java version necessary for the Node builds
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    java <- (ThisBuild / githubWorkflowJavaVersions).value.tail
  } yield MatrixExclude(Map("ci" -> "ciNodeJS", "java" -> java.render))
}

// On the JVM Build all Javas for one Scala, and all Scalas for one Java
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    scala <- (ThisBuild / crossScalaVersions).value.tail
    java <- (ThisBuild / githubWorkflowJavaVersions).value.tail
  } yield MatrixExclude(Map("ci" -> "ciJVM", "scala" -> scala, "java" -> java.render))
}

addCommandAlias("ciJVM", "; project rootJVM")
addCommandAlias("ciNodeJS", "; project rootJS")

enablePlugins(SonatypeCiReleasePlugin)

versionIntroduced.withRank(KeyRanks.Invisible) := Map(
  scala_3 -> "0.22.0"
)

lazy val modules: List[ProjectReference] = jvmModules ++ jsModules

lazy val jvmModules: List[ProjectReference] = List(
  core.jvm,
  laws.jvm,
  testing.jvm,
  tests.jvm,
  server.jvm,
  prometheusMetrics,
  client.jvm,
  dropwizardMetrics,
  emberCore.jvm,
  emberServer.jvm,
  emberClient.jvm,
  blazeCore,
  blazeServer,
  blazeClient,
  asyncHttpClient,
  jettyServer,
  jettyClient,
  okHttpClient,
  servlet,
  tomcatServer,
  theDsl.jvm,
  jawn.jvm,
  boopickle.jvm,
  circe.jvm,
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
  scalafixInternalInput,
  scalafixInternalOutput,
  scalafixInternalTests,
)

lazy val jsModules: List[ProjectReference] = List(
  core.js,
  laws.js,
  testing.js,
  tests.js,
  server.js,
  client.js,
  emberCore.js,
  emberServer.js,
  emberClient.js,
  nodeServerless,
  theDsl.js,
  jawn.js,
  boopickle.js,
  circe.js,
)

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(
    // Root project
    name := "http4s",
    description := "A minimal, Scala-idiomatic library for HTTP",
    startYear := Some(2013),
  )
  .aggregate(modules: _*)

lazy val rootJVM = project
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .aggregate(jvmModules: _*)

lazy val rootJS = project
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .aggregate(jsModules: _*)

lazy val core = libraryCrossProject("core")
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
      BuildInfoKey.map(http4sApiVersion) { case (_, v) => "apiVersion" -> v },
    ),
    buildInfoPackage := organization.value,
    libraryDependencies ++= Seq(
      caseInsensitive.value,
      catsCore.value,
      catsEffectStd.value,
      catsParse.value.exclude("org.typelevel", "cats-core_2.13"),
      crypto.value,
      fs2Core.value,
      fs2Io.value,
      ip4sCore.value,
      literally.value,
      log4s.value,
      munit.value % Test,
      scodecBits.value,
      vault.value,
    ),
    libraryDependencies ++= {
      if (isDotty.value) Seq.empty
      else
        Seq(
          scalaReflect(scalaVersion.value) % Provided
        )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang", "scala-reflect"),
    mimaBinaryIssueFilters ++= Seq(
      // These will only cause problems when called via Java interop
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("org.http4s.HttpApp.apply"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("org.http4s.HttpApp.local"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.internal.Logger.logMessageWithBodyText"),

      // private constructors so effectively final already
      ProblemFilters.exclude[FinalClassProblem]("org.http4s.internal.CharPredicate$General"),
      ProblemFilters.exclude[FinalClassProblem]("org.http4s.internal.CharPredicate$ArrayBased"),
      ProblemFilters.exclude[FinalClassProblem]("org.http4s.internal.CharPredicate$RangeBased"),
      ProblemFilters.exclude[FinalClassProblem]("org.http4s.internal.CharPredicate$MaskBased"),
    ),
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      slf4jApi // residual dependency from macros
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalaJavaLocalesEnUS.value,
      scalaJavaTime.value,
    )
  )

lazy val laws = libraryCrossProject("laws", CrossType.Pure)
  .settings(
    description := "Instances and laws for testing http4s code",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      caseInsensitiveTesting.value,
      catsEffect.value,
      catsEffectTestkit.value,
      catsLaws.value,
      disciplineCore.value,
      ip4sTestKit.value,
      scalacheck.value,
      scalacheckEffectMunit.value,
      munitCatsEffect.value,
    ),
    unusedCompileDependenciesFilter -= moduleFilter(
      organization = "org.typelevel",
      name = "scalacheck-effect-munit",
    ),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.laws.discipline.ArbitraryInstances#ParseResultSyntax.this"
      ) // private
    ),
  )
  .dependsOn(core)

lazy val testing = libraryCrossProject("testing", CrossType.Full)
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Internal utilities for http4s tests",
    startYear := Some(2016),
    libraryDependencies ++= Seq(
      catsEffectLaws.value,
      munitCatsEffect.value,
      munitDiscipline.value,
      scalacheck.value,
      scalacheckEffect.value,
      scalacheckEffectMunit.value,
    ).map(_ % Test),
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalaJavaTimeTzdb.value
    ).map(_ % Test)
  )
  .dependsOn(laws)

// Defined outside core/src/test so it can depend on published testing
lazy val tests = libraryCrossProject("tests")
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Tests for core project",
    startYear := Some(2013),
  )
  .dependsOn(core, testing % "test->test")

lazy val server = libraryCrossProject("server")
  .settings(
    description := "Base library for building http4s servers",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.server.middleware.CSRF.this"
      ), // private[middleware]
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.server.middleware.CSRF#CSRFBuilder.this"
      ), // private[middleware]
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.server.middleware.authentication.DigestUtil.computeResponse"
      ), // private[authentication]
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.server.middleware.GZip$TrailerGen"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.server.middleware.GZip$TrailerGen$"
      ), // private
      // the following are private[middleware]
      ProblemFilters
        .exclude[FinalClassProblem]("org.http4s.server.middleware.CORSPolicy$AllowHeaders$In"),
      ProblemFilters
        .exclude[FinalClassProblem]("org.http4s.server.middleware.CORSPolicy$AllowHeaders$Static"),
      ProblemFilters
        .exclude[FinalClassProblem]("org.http4s.server.middleware.CORSPolicy$AllowMethods$In"),
      ProblemFilters
        .exclude[FinalClassProblem]("org.http4s.server.middleware.CORSPolicy$AllowOrigin$Match"),
      ProblemFilters
        .exclude[FinalClassProblem]("org.http4s.server.middleware.CORSPolicy$ExposeHeaders$In"),
      ProblemFilters
        .exclude[FinalClassProblem]("org.http4s.server.middleware.CORSPolicy$MaxAge$Some"),
    ),
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
    core.jvm % "compile->compile",
    theDsl.jvm % "test->compile",
    testing.jvm % "test->test",
    server.jvm % "test->compile",
    client.jvm % "test->compile",
  )

lazy val client = libraryCrossProject("client")
  .settings(
    description := "Base library for building http4s clients",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters
        .exclude[Problem]("org.http4s.client.oauth1.package.genAuthHeader"), // private[oauth1]
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.oauth1.package.makeSHASig"
      ), // private[oauth1]
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.oauth1.*.generateHMAC"
      ), // private[oauth1]
    ),
  )
  .dependsOn(core, server, testing % "test->test", theDsl % "test->compile")
  .jsConfigure(_.dependsOn(nodeServerless % Test))

lazy val dropwizardMetrics = libraryProject("dropwizard-metrics")
  .settings(
    description := "Support for Dropwizard Metrics",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      dropwizardMetricsCore,
      dropwizardMetricsJson,
    ),
  )
  .dependsOn(
    core.jvm % "compile->compile",
    testing.jvm % "test->test",
    theDsl.jvm % "test->compile",
    client.jvm % "test->compile",
    server.jvm % "test->compile",
  )

lazy val emberCore = libraryCrossProject("ember-core", CrossType.Full)
  .settings(
    description := "Base library for ember http4s clients and servers",
    startYear := Some(2019),
    unusedCompileDependenciesFilter -= moduleFilter("io.chrisdavenport", "log4cats-core"),
    libraryDependencies ++= Seq(
      log4catsTesting.value % Test
    ),
    scalacOptions --= Seq(
      "-Ywarn-numeric-widen",
      "-Werror",
      "-Xfatal-warnings",
    ),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Encoder.reqToBytes"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#HeaderP.apply"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#HeaderP.copy"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#HeaderP.parseHeaders"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#HeaderP.this"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#MessageP.apply"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#MessageP.parseMessage"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.core.Parser#MessageP.unapply"),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.core.Parser#Request#ReqPrelude.parsePrelude"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.core.Parser#Response#RespPrelude.parsePrelude"
      ),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.ember.core.EmptyStreamError"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.ember.core.EmptyStreamError$"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.ember.core.Parser$MessageP"),
      ProblemFilters
        .exclude[MissingClassProblem]("org.http4s.ember.core.Parser$MessageP$EndOfStreamError"),
      ProblemFilters
        .exclude[MissingClassProblem]("org.http4s.ember.core.Parser$MessageP$EndOfStreamError$"),
      ProblemFilters
        .exclude[MissingClassProblem]("org.http4s.ember.core.Parser$MessageP$MessageTooLongError"),
      ProblemFilters
        .exclude[MissingClassProblem]("org.http4s.ember.core.Parser$MessageP$MessageTooLongError$"),
      ProblemFilters.exclude[MissingTypesProblem]("org.http4s.ember.core.Parser$MessageP$"),
    ),
  )
  .jvmSettings(
    libraryDependencies += "com.twitter" % "hpack" % "1.0.2"
  )
  .jsEnablePlugins(ScalaJSBundlerPlugin)
  .jsSettings(
    Compile / npmDependencies += "hpack.js" -> "2.1.6",
    useYarn := true,
    yarnExtraArgs += "--frozen-lockfile",
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )
  .dependsOn(core, testing % "test->test")

lazy val emberServer = libraryCrossProject("ember-server")
  .settings(
    description := "ember implementation for http4s servers",
    startYear := Some(2019),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.EmberServerBuilder#Defaults.maxConcurrency"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.internal.ServerHelpers.isKeepAlive"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.EmberServerBuilder#Defaults.maxConcurrency"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.ember.server.internal.ServerHelpers.runApp"
      ),
      ProblemFilters.exclude[Problem]("org.http4s.ember.server.EmberServerBuilder.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.internal.ServerHelpers.runApp"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.internal.ServerHelpers.runConnection"
      ),
    ),
    Test / parallelExecution := false,
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      log4catsSlf4j,
      javaWebSocket % Test,
      jnrUnixSocket % Test, // Necessary for jdk < 16
    )
  )
  .jsEnablePlugins(ScalaJSBundlerPlugin)
  .jsSettings(
    libraryDependencies ++= Seq(
      log4catsNoop.value
    ),
    useYarn := true,
    yarnExtraArgs += "--frozen-lockfile",
  )
  .dependsOn(
    emberCore % "compile;test->test",
    server % "compile;test->test",
    emberClient % "test->compile",
  )

lazy val emberClient = libraryCrossProject("ember-client")
  .settings(
    description := "ember implementation for http4s clients",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      keypool.value,
      log4catsSlf4j,
    ),
    mimaBinaryIssueFilters := Seq(
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.client.EmberClientBuilder.this")
    ),
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      log4catsSlf4j
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      log4catsNoop.value
    ),
  )
  .dependsOn(emberCore % "compile;test->test", client % "compile;test->test")

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
    description := "Base library for binding blaze to http4s clients and servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      blazeHttp
    ),
  )
  .dependsOn(core.jvm, testing.jvm % "test->test")

lazy val blazeServer = libraryProject("blaze-server")
  .settings(
    description := "blaze implementation for http4s servers",
    startYear := Some(2014),
    mimaBinaryIssueFilters := Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.blaze.server.BlazeServerBuilder.this"
      ), // private
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.blaze.server.WebSocketDecoder.this"
      ), // private
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.blaze.server.BlazeServerBuilder.this"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.blaze.server.BlazeServerBuilder$ExecutionContextConfig"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.blaze.server.BlazeServerBuilder$ExecutionContextConfig$"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.blaze.server.BlazeServerBuilder$ExecutionContextConfig$DefaultContext$"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.blaze.server.BlazeServerBuilder$ExecutionContextConfig$ExplicitContext"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.blaze.server.BlazeServerBuilder$ExecutionContextConfig$ExplicitContext$"
      ), // private
    ),
  )
  .dependsOn(blazeCore % "compile;test->test", server.jvm % "compile;test->test")

lazy val blazeClient = libraryProject("blaze-client")
  .settings(
    description := "blaze implementation for http4s clients",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      // private constructor
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.BlazeClientBuilder.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.Http1Support.this"),
    ),
  )
  .dependsOn(blazeCore % "compile;test->test", client.jvm % "compile;test->test")

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
    Test / parallelExecution := false,
  )
  .dependsOn(core.jvm, testing.jvm % "test->test", client.jvm % "compile;test->test")

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
  .dependsOn(core.jvm, testing.jvm % "test->test", client.jvm % "compile;test->test")

lazy val nodeServerless = libraryProject("node-serverless")
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .settings(
    description := "Node.js serverless wrapper for http4s apps",
    startYear := Some(2021),
  )
  .dependsOn(core.js)

lazy val okHttpClient = libraryProject("okhttp-client")
  .settings(
    description := "okhttp implementation for http4s clients",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.okhttp,
      okio,
    ),
  )
  .dependsOn(core.jvm, testing.jvm % "test->test", client.jvm % "compile;test->test")

lazy val servlet = libraryProject("servlet")
  .settings(
    description := "Portable servlet implementation for http4s servers",
    startYear := Some(2013),
    libraryDependencies ++= Seq(
      javaxServletApi % Provided,
      Http4sPlugin.jettyServer % Test,
      jettyServlet % Test,
      Http4sPlugin.asyncHttpClient % Test,
    ),
  )
  .dependsOn(server.jvm % "compile;test->test")

lazy val jettyServer = libraryProject("jetty-server")
  .settings(
    description := "Jetty implementation for http4s servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      jettyHttp2Server,
      Http4sPlugin.jettyServer,
      jettyServlet,
      jettyUtil,
    ),
  )
  .dependsOn(servlet % "compile;test->test", theDsl.jvm % "test->test")

lazy val tomcatServer = libraryProject("tomcat-server")
  .settings(
    description := "Tomcat implementation for http4s servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      tomcatCatalina,
      tomcatCoyote,
      tomcatUtilScan,
    ),
  )
  .dependsOn(servlet % "compile;test->test")

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryCrossProject("dsl", CrossType.Pure)
  .settings(
    description := "Simple DSL for writing http4s services",
    startYear := Some(2013),
  )
  .dependsOn(core, testing % "test->test")

lazy val jawn = libraryCrossProject("jawn", CrossType.Pure)
  .settings(
    description := "Base library to parse JSON to various ASTs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      jawnFs2.value,
      jawnParser.value,
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val boopickle = libraryCrossProject("boopickle", CrossType.Pure)
  .settings(
    description := "Provides Boopickle codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.boopickle.value
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryCrossProject("circe", CrossType.Pure)
  .settings(
    description := "Provides Circe codecs for http4s",
    startYear := Some(2015),
    libraryDependencies ++= Seq(
      circeCore.value,
      circeTesting.value % Test,
    ),
  )
  .jvmSettings(libraryDependencies += circeJawn.value)
  .jsSettings(libraryDependencies += circeJawn15.value)
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val playJson = libraryProject("play-json")
  .settings(
    description := "Provides Play json codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.playJson.cross(CrossVersion.for3Use2_13)
    ),
    publish / skip := isDotty.value,
    compile / skip := isDotty.value,
  )
  .dependsOn(jawn.jvm % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
    description := "Provides scala-xml codecs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      Http4sPlugin.scalaXml
    ),
  )
  .dependsOn(core.jvm, testing.jvm % "test->test")

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
    publish / skip := isDotty.value,
  )
  .enablePlugins(SbtTwirl)
  .dependsOn(core.jvm, testing.jvm % "test->test")

lazy val scalatags = http4sProject("scalatags")
  .settings(
    description := "Scalatags template support for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      scalatagsApi.cross(CrossVersion.for3Use2_13)
    ),
    publish / skip := isDotty.value,
  )
  .dependsOn(core.jvm, testing.jvm % "test->test")

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
  .dependsOn(core.jvm, circe.jvm, emberCore.jvm)

lazy val docs = http4sProject("docs")
  .enablePlugins(
    GhpagesPlugin,
    NoPublishPlugin,
    ScalaUnidocPlugin,
    MdocPlugin,
    LaikaPlugin,
  )
  .settings(docsProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      circeGeneric,
      circeLiteral,
      cryptobits,
    ),
    description := "Documentation for http4s",
    startYear := Some(2013),
    autoAPIMappings := true,
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        (List[ProjectReference](
          bench,
          examples,
          examplesBlaze,
          examplesDocker,
          examplesJetty,
          examplesTomcat,
          examplesWar,
          scalafixInternalInput,
          scalafixInternalOutput,
          scalafixInternalRules,
          scalafixInternalTests,
        ) ++ jsModules): _*
      ),
    mdocIn := (Compile / sourceDirectory).value / "mdoc",
    fatalWarningsInCI := false,
    laikaExtensions := SiteConfig.extensions,
    laikaConfig := SiteConfig.config(versioned = true).value,
    laikaTheme := SiteConfig.theme(
      currentVersion = SiteConfig.versions.current,
      SiteConfig.variables.value,
      SiteConfig.homeURL.value,
      includeLandingPage = false,
    ),
    laikaDescribe := "<disabled>",
    laikaIncludeEPUB := true,
    laikaIncludePDF := false,
    Laika / sourceDirectories := Seq(mdocOut.value),
    ghpagesPrivateMappings := (laikaSite / mappings).value ++ {
      val docsPrefix = extractDocsPrefix(version.value)
      for ((f, d) <- (ScalaUnidoc / packageDoc / mappings).value)
        yield (f, s"$docsPrefix/api/$d")
    },
    ghpagesCleanSite / includeFilter := {
      new FileFilter {
        val docsPrefix = extractDocsPrefix(version.value)
        def accept(f: File): Boolean = f.getCanonicalPath.startsWith(
          (ghpagesRepository.value / s"${docsPrefix}").getCanonicalPath
        )
      }
    },
    apiMappings ++= {
      ScaladocApiMapping.mappings(
        (ScalaUnidoc / unidoc / unidocAllClasspaths).value,
        scalaBinaryVersion.value,
      )
    },
  )
  .dependsOn(
    client.jvm,
    core.jvm,
    theDsl.jvm,
    blazeServer,
    blazeClient,
    circe.jvm,
    dropwizardMetrics,
    prometheusMetrics,
  )

lazy val website = http4sProject("website")
  .enablePlugins(GhpagesPlugin, LaikaPlugin, NoPublishPlugin)
  .settings(docsProjectSettings)
  .settings(
    description := "Common area of http4s.org",
    startYear := Some(2013),
    laikaExtensions := SiteConfig.extensions,
    laikaConfig := SiteConfig.config(versioned = false).value,
    laikaTheme := SiteConfig.theme(
      currentVersion = SiteConfig.versions.current,
      SiteConfig.variables.value,
      SiteConfig.homeURL.value,
      includeLandingPage = false,
    ),
    laikaDescribe := "<disabled>",
    Laika / sourceDirectories := Seq(
      baseDirectory.value / "src" / "hugo" / "content",
      baseDirectory.value / "src" / "hugo" / "static",
    ),
    ghpagesNoJekyll := true,
    ghpagesPrivateMappings := (laikaSite / mappings).value,
    ghpagesCleanSite / excludeFilter :=
      new FileFilter {
        val v = ghpagesRepository.value.getCanonicalPath + "/v"
        def accept(f: File) =
          f.getCanonicalPath.startsWith(v) &&
            f.getCanonicalPath.charAt(v.size).isDigit
      },
  )

lazy val examples = http4sProject("examples")
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Common code for http4s examples",
    startYear := Some(2013),
    libraryDependencies ++= Seq(
      circeGeneric % Runtime,
      logbackClassic % Runtime,
    ),
    // todo enable when twirl supports dotty TwirlKeys.templateImports := Nil,
  )
  .dependsOn(server.jvm, dropwizardMetrics, theDsl.jvm, circe.jvm, scalaXml /*, twirl*/ )
// todo enable when twirl supports dotty .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    startYear := Some(2013),
    fork := true,
    libraryDependencies ++= Seq(
      circeGeneric
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
  .dependsOn(emberServer.jvm, emberClient.jvm)

lazy val exampleEmberServerH2 = exampleJSProject("examples-ember-server-h2")
  .dependsOn(emberServer.js)

lazy val exampleEmberClientH2 = exampleJSProject("examples-ember-client-h2")
  .dependsOn(emberClient.js)

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
  .dependsOn(blazeServer, theDsl.jvm)

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

lazy val scalafixInternalRules = project
  .in(file("scalafix-internal/rules"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % _root_.scalafix.sbt.BuildInfo.scalafixVersion
    ).filter(_ => !isDotty.value)
  )

lazy val scalafixInternalInput = project
  .in(file("scalafix-internal/input"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(headerSources / excludeFilter := AllPassFilter, scalacOptions -= "-Xfatal-warnings")
  .dependsOn(core.jvm)

lazy val scalafixInternalOutput = project
  .in(file("scalafix-internal/output"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(headerSources / excludeFilter := AllPassFilter, scalacOptions -= "-Xfatal-warnings")
  .dependsOn(core.jvm)

lazy val scalafixInternalTests = project
  .in(file("scalafix-internal/tests"))
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(ScalafixTestkitPlugin)
  .settings(
    libraryDependencies ++= Seq(
      ("ch.epfl.scala" %% "scalafix-testkit" % _root_.scalafix.sbt.BuildInfo.scalafixVersion % Test)
        .cross(CrossVersion.full)
    ).filter(_ => !isDotty.value),
    Compile / compile :=
      (Compile / compile).dependsOn(scalafixInternalInput / Compile / compile).value,
    scalafixTestkitOutputSourceDirectories :=
      (scalafixInternalOutput / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (scalafixInternalInput / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (scalafixInternalInput / Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions := (scalafixInternalInput / Compile / scalacOptions).value,
    scalacOptions += "-Yrangepos",
  )
  .settings(headerSources / excludeFilter := AllPassFilter)
  .disablePlugins(ScalafixPlugin)
  .dependsOn(scalafixInternalRules)

def http4sProject(name: String) =
  Project(name, file(name))
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name"
    )
    .enablePlugins(Http4sPlugin)
    .dependsOn(scalafixInternalRules % ScalafixConfig)

def http4sCrossProject(name: String, crossType: CrossType) =
  sbtcrossproject
    .CrossProject(name, file(name))(JVMPlatform, JSPlatform)
    .withoutSuffixFor(JVMPlatform)
    .crossType(crossType)
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      Test / test := {
        val result = (Test / test).value
        if (crossType == CrossType.Full) { // Check for misplaced srcs
          val dir = baseDirectory.value / ".." / "src"
          if (dir.exists) sys.error(s"Misplaced sources in ${dir.toPath().normalize()}")
        }
        result
      },
    )
    .jsSettings(
      Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
    )
    .enablePlugins(Http4sPlugin)
    .jsConfigure(_.disablePlugins(DoctestPlugin))
    .configure(_.dependsOn(scalafixInternalRules % ScalafixConfig))

def libraryProject(name: String) = http4sProject(name)
def libraryCrossProject(name: String, crossType: CrossType = CrossType.Full) =
  http4sCrossProject(name, crossType)

def exampleProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(NoPublishPlugin)
    .settings(libraryDependencies += logbackClassic % Runtime)
    .dependsOn(examples)

def exampleJSProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(NoPublishPlugin, ScalaJSBundlerPlugin)
    .settings(
      useYarn := true,
      yarnExtraArgs += "--frozen-lockfile",
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    )
    .dependsOn(theDsl.js)

lazy val commonSettings = Seq(
  Compile / doc / scalacOptions += "-no-link-warnings",
  scalacOptions ++= {
    // Enables fatal warnings for Scala 3 in CI
    if (isDotty.value && githubIsWorkflowBuild.value)
      Seq("-Xfatal-warnings")
    else
      Seq.empty
  },
  libraryDependencies ++= Seq(
    catsLaws.value,
    logbackClassic,
    scalacheck.value,
  ).map(_ % Test),
  apiURL := Some(url(s"https://http4s.org/v${baseVersion.value}/api")),
)

def initCommands(additionalImports: String*) =
  initialCommands := (List(
    "fs2._",
    "cats._",
    "cats.data._",
    "cats.effect._",
    "cats.implicits._",
  ) ++ additionalImports).mkString("import ", ", ", "")

// Everything is driven through release steps and the http4s* variables
// This won't actually release unless on Travis.
addCommandAlias("ci", ";clean ;release with-defaults")

// OrganizeImports needs to run separately to clean up after the other rules
addCommandAlias(
  "quicklint",
  ";scalafixAll --triggered ;scalafixAll ;scalafmtAll ;scalafmtSbt",
)

addCommandAlias(
  "lint",
  ";clean ;+test:compile ;+scalafixAll --triggered ;+scalafixAll ;+scalafmtAll ;scalafmtSbt ;+mimaReportBinaryIssues",
)
