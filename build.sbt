import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._
import org.http4s.sbt.ScaladocApiMapping

import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / crossScalaVersions := Seq(scala_3, scala_212, scala_213)
ThisBuild / tlBaseVersion := "0.22"
ThisBuild / developers += tlGitHubDev("rossabaker", "Ross A. Baker")

ThisBuild / tlCiReleaseBranches := Seq("series/0.22")
ThisBuild / tlSitePublishBranch := Some("series/0.22")

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"

ThisBuild / tlFatalWarningsInCi := !tlIsScala3.value

ThisBuild / scalafixAll / skip := tlIsScala3.value
ThisBuild / ScalafixConfig / skip := tlIsScala3.value
ThisBuild / Test / scalafixConfig := Some(file(".scalafix.test.conf"))

ThisBuild / githubWorkflowBuild ++= Seq(
  WorkflowStep.Sbt(
    List("scalafixAll --check"),
    name = Some("Check Scalafix rules"),
    cond = Some(s"matrix.scala != '$scala_3'"),
  )
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
  unidocs,
  examples,
  examplesBlaze,
  examplesDocker,
  examplesEmber,
  examplesJetty,
  examplesTomcat,
  examplesWar,
  scalafixInternalRules,
  scalafixInternalInput,
  scalafixInternalOutput,
  scalafixInternalTests,
)

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(
    // Root project
    name := "http4s",
    description := "A minimal, Scala-idiomatic library for HTTP",
    startYear := Some(2013),
  )
  .aggregate(modules: _*)
  .dependsOn(scalafixInternalRules % ScalafixConfig)

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
      BuildInfoKey.map(http4sApiVersion) { case (_, v) => "apiVersion" -> v },
    ),
    buildInfoPackage := organization.value,
    libraryDependencies ++= Seq(
      caseInsensitive,
      catsCore,
      catsEffect,
      catsParse.exclude("org.typelevel", "cats-core_2.13"),
      crypto,
      fs2Core,
      fs2Io,
      ip4sCore,
      literally,
      log4s,
      munit % Test,
      scodecBits,
      vault,
    ),
    libraryDependencies ++= {
      if (tlIsScala3.value) Seq.empty
      else
        Seq(
          slf4jApi, // residual dependency from macros
          scalaReflect(scalaVersion.value) % Provided,
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
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters
            .exclude[IncompatibleResultTypeProblem]("org.http4s.headers.Max-Forwards.parser"),
          ProblemFilters
            .exclude[IncompatibleResultTypeProblem]("org.http4s.headers.Max-Forwards.parser"),
          ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.headers.Server.parser"),
          ProblemFilters
            .exclude[IncompatibleResultTypeProblem]("org.http4s.headers.Upgrade.parser"),
          ProblemFilters.exclude[MissingClassProblem]("org.http4s.syntax.LiteralsSyntax$Validator"),
          ProblemFilters
            .exclude[MissingClassProblem]("org.http4s.syntax.LiteralsSyntax$mediatype$"),
          ProblemFilters.exclude[MissingClassProblem]("org.http4s.syntax.LiteralsSyntax$qvalue$"),
          ProblemFilters.exclude[MissingClassProblem]("org.http4s.syntax.LiteralsSyntax$uri$"),
          ProblemFilters.exclude[MissingClassProblem]("org.http4s.syntax.LiteralsSyntax$uripath$"),
          ProblemFilters.exclude[MissingClassProblem]("org.http4s.syntax.LiteralsSyntax$urischeme$"),
        )
      else Seq.empty
    },
  )

lazy val laws = libraryProject("laws")
  .settings(
    description := "Instances and laws for testing http4s code",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      caseInsensitiveTesting,
      catsEffectLaws,
      catsLaws,
      disciplineCore,
      ip4sTestKit,
      scalacheck,
      scalacheckEffectMunit,
      munitCatsEffect,
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

lazy val testing = libraryProject("testing")
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Internal utilities for http4s tests",
    startYear := Some(2016),
    libraryDependencies ++= Seq(
      catsEffectLaws,
      scalacheck,
      munitCatsEffect,
      munitDiscipline,
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
    libraryDependencies ++= Seq(
      scalacCompatAnnotation
    ),
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
    buildInfoKeys := Seq[BuildInfoKey](
      Test / resourceDirectory
    ),
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
    client % "test->compile",
  )

lazy val client = libraryProject("client")
  .settings(
    description := "Base library for building http4s clients",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      nettyBuffer % Test,
      nettyCodecHttp % Test,
    ),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters
        .exclude[Problem]("org.http4s.client.oauth1.package.genAuthHeader"), // private[oauth1]
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.oauth1.package.makeSHASig"
      ), // private[oauth1]
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.oauth1.*.generateHMAC"
      ), // private[oauth1]
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.client.oauth1.package.SHA1"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.client.oauth1.package.UTF_8"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.client.oauth1.package.bytes"),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.WaitQueueTimeoutException.getStackTraceDepth"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.WaitQueueTimeoutException.getStackTraceElement"
          ),
        )
      else Seq.empty
    },
  )
  .dependsOn(core, testing % "test->test", server % "test->compile", theDsl % "test->compile")

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
    core % "compile->compile",
    testing % "test->test",
    theDsl % "test->compile",
    client % "test->compile",
    server % "test->compile",
  )

lazy val emberCore = libraryProject("ember-core")
  .settings(
    description := "Base library for ember http4s clients and servers",
    startYear := Some(2019),
    unusedCompileDependenciesFilter -= moduleFilter("io.chrisdavenport", "log4cats-core"),
    libraryDependencies ++= Seq(
      log4catsTesting % Test
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
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          // private[ember]
          ProblemFilters
            .exclude[MissingFieldProblem](
              "org.http4s.ember.core.Parser#MessageP.MessageTooLongError"
            ),
          ProblemFilters
            .exclude[MissingFieldProblem]("org.http4s.ember.core.Parser#MessageP.EndOfStreamError"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem](
              "org.http4s.ember.core.Parser#MessageP.fromProduct"
            ),
        )
      else Seq.empty
    },
  )
  .dependsOn(core, testing % "test->test")

lazy val emberServer = libraryProject("ember-server")
  .settings(
    description := "ember implementation for http4s servers",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      log4catsSlf4j,
      javaWebSocket % Test,
    ),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.EmberServerBuilder#Defaults.maxConcurrency"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.server.internal.ServerHelpers.isKeepAlive"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.ember.server.internal.ServerHelpers.runApp"
      ),
    ),
    Test / parallelExecution := false,
  )
  .dependsOn(
    emberCore % "compile;test->test",
    server % "compile;test->test",
    emberClient % "test->compile",
  )

lazy val emberClient = libraryProject("ember-client")
  .settings(
    description := "ember implementation for http4s clients",
    startYear := Some(2019),
    libraryDependencies ++= Seq(
      keypool,
      log4catsSlf4j,
    ),
    mimaBinaryIssueFilters := Seq(
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.client.EmberClientBuilder.this")
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
  .dependsOn(core, testing % "test->test")

lazy val blazeServer = libraryProject("blaze-server")
  .settings(
    description := "blaze implementation for http4s servers",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.BlazeServerBuilder.this"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.WebSocketDecoder.this"),
    ),
  )
  .dependsOn(blazeCore % "compile;test->test", server % "compile;test->test")

lazy val blazeClient = libraryProject("blaze-client")
  .settings(
    description := "blaze implementation for http4s clients",
    startYear := Some(2014),
    mimaBinaryIssueFilters ++= Seq(
      // These are all private to blaze-client and fallout from from
      // the deprecation of org.http4s.client.Connection
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.BasicManager.invalidate"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.BasicManager.release"),
      ProblemFilters.exclude[MissingTypesProblem]("org.http4s.blaze.client.BlazeConnection"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.ConnectionManager.release"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.blaze.client.ConnectionManager.invalidate"
      ),
      ProblemFilters
        .exclude[ReversedMissingMethodProblem]("org.http4s.blaze.client.ConnectionManager.release"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.blaze.client.ConnectionManager.invalidate"
      ),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.blaze.client.ConnectionManager#NextConnection.connection"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.blaze.client.ConnectionManager#NextConnection.copy"
      ),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.blaze.client.ConnectionManager#NextConnection.copy$default$1"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.blaze.client.ConnectionManager#NextConnection.this"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.blaze.client.ConnectionManager#NextConnection.apply"
      ),
      ProblemFilters.exclude[MissingTypesProblem]("org.http4s.blaze.client.Http1Connection"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.PoolManager.release"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.PoolManager.invalidate"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.BasicManager.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.ConnectionManager.pool"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.ConnectionManager.basic"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("org.http4s.blaze.client.PoolManager.this"),
      // inside private trait/clas/object
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.client.BlazeConnection.runRequest"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.blaze.client.BlazeConnection.runRequest"
      ),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.client.Http1Connection.runRequest"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.client.Http1Connection.resetWrite"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.blaze.client.Http1Connection$Idle"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.blaze.client.Http1Connection$Idle$"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.blaze.client.Http1Connection$Read$"),
      ProblemFilters
        .exclude[MissingClassProblem]("org.http4s.blaze.client.Http1Connection$ReadWrite$"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.blaze.client.Http1Connection$Write$"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.blaze.client.Http1Connection.isRecyclable"
      ),
      ProblemFilters
        .exclude[IncompatibleResultTypeProblem]("org.http4s.blaze.client.Connection.isRecyclable"),
      ProblemFilters
        .exclude[ReversedMissingMethodProblem]("org.http4s.blaze.client.Connection.isRecyclable"),
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.blaze.client.ConnectionManager#NextConnection._1"
          )
        )
      else Seq.empty
    },
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
    Test / parallelExecution := false,
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
      scalaJava8Compat,
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
      Http4sPlugin.asyncHttpClient % Test,
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
    ),
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
    ),
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
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val boopickle = libraryProject("boopickle")
  .settings(
    description := "Provides Boopickle codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.boopickle
    ),
    tlVersionIntroduced ~= { _.updated("3", "0.22.1") },
  )
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryProject("circe")
  .settings(
    description := "Provides Circe codecs for http4s",
    startYear := Some(2015),
    libraryDependencies ++= Seq(
      circeCore,
      circeJawn,
      circeTesting % Test,
    ),
  )
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val playJson = libraryProject("play-json")
  .settings(
    description := "Provides Play json codecs for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      if (tlIsScala3.value)
        Http4sPlugin.playJson.cross(CrossVersion.for3Use2_13)
      else
        Http4sPlugin.playJson
    ),
    publish / skip := tlIsScala3.value,
    compile / skip := tlIsScala3.value,
    skipUnusedDependenciesTestOnScala3,
    mimaPreviousArtifacts := { if (tlIsScala3.value) Set.empty else mimaPreviousArtifacts.value },
  )
  .dependsOn(jawn % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
    description := "Provides scala-xml codecs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      Http4sPlugin.scalaXml
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
        case module if module.name == "twirl-api" && tlIsScala3.value =>
          module.cross(CrossVersion.for3Use2_13)
        case module => module
      }
    },
    publish / skip := tlIsScala3.value,
    skipUnusedDependenciesTestOnScala3,
    mimaPreviousArtifacts := { if (tlIsScala3.value) Set.empty else mimaPreviousArtifacts.value },
  )
  .enablePlugins(SbtTwirl)
  .dependsOn(core, testing % "test->test")

lazy val scalatags = http4sProject("scalatags")
  .settings(
    description := "Scalatags template support for http4s",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      if (tlIsScala3.value)
        scalatagsApi.cross(CrossVersion.for3Use2_13)
      else
        scalatagsApi
    ),
    publish / skip := tlIsScala3.value,
    skipUnusedDependenciesTestOnScala3,
    mimaPreviousArtifacts := { if (tlIsScala3.value) Set.empty else mimaPreviousArtifacts.value },
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
  .dependsOn(core, circe, emberCore)

lazy val unidocs = http4sProject("unidocs")
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    moduleName := "http4s-docs",
    description := "Unified API documentation for http4s",
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
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
        docs,
      ),
    apiMappings ++= {
      ScaladocApiMapping.mappings(
        (ScalaUnidoc / unidoc / unidocAllClasspaths).value,
        scalaBinaryVersion.value,
      )
    },
  )

lazy val docs = http4sProject("site")
  .enablePlugins(Http4sSitePlugin)
  .settings(
    libraryDependencies ++= Seq(
      circeGeneric,
      circeLiteral,
      cryptobits,
    ),
    description := "Documentation for http4s",
    tlFatalWarningsInCi := false,
    fork := false,
  )
  .dependsOn(
    client,
    core,
    theDsl,
    blazeServer,
    blazeClient,
    circe,
    dropwizardMetrics,
    prometheusMetrics,
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
  .dependsOn(server, dropwizardMetrics, theDsl, circe, scalaXml /*, twirl*/ )
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

lazy val scalafixInternalRules = project
  .in(file("scalafix-internal/rules"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(
    startYear := Some(2021),
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % V.scalafix
    ).filter(_ => !tlIsScala3.value),
  )

lazy val scalafixInternalInput = project
  .in(file("scalafix-internal/input"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(headerSources / excludeFilter := AllPassFilter, scalacOptions -= "-Xfatal-warnings")
  .dependsOn(core)

lazy val scalafixInternalOutput = project
  .in(file("scalafix-internal/output"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(headerSources / excludeFilter := AllPassFilter, scalacOptions -= "-Xfatal-warnings")
  .dependsOn(core)

lazy val scalafixInternalTests = project
  .in(file("scalafix-internal/tests"))
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(ScalafixTestkitPlugin)
  .settings(
    libraryDependencies := {
      if (tlIsScala3.value)
        libraryDependencies.value.filterNot(_.name == "scalafix-testkit")
      else
        libraryDependencies.value
    },
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
  .settings(
    dependencyOverrides ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % V.scalafix,
      "ch.epfl.scala" %% "scalafix-testkit" % V.scalafix % Test cross CrossVersion.full,
    )
  )

def http4sProject(name: String) =
  Project(name, file(name))
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      testFrameworks += new TestFramework("munit.Framework"),
      initCommands(),
    )
    .enablePlugins(Http4sPlugin)
    .dependsOn(scalafixInternalRules % ScalafixConfig)

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
)

lazy val skipUnusedDependenciesTestOnScala3 = Seq(
  unusedCompileDependenciesTest := Def.taskDyn {
    val skip = tlIsScala3.value
    Def.task {
      if (!skip) unusedCompileDependenciesTest.value
    }
  }
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

addCommandAlias(
  "quicklint",
  ";scalafixAll --triggered ;scalafixAll ;scalafmtAll ;scalafmtSbt",
)

addCommandAlias(
  "lint",
  ";clean ;+test:compile ;scalafixAll --triggered ;scalafixAll ;+scalafmtAll ;scalafmtSbt ;+mimaReportBinaryIssues",
)
