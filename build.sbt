import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._
import org.http4s.sbt.ScaladocApiMapping

import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / crossScalaVersions := Seq(scala_3, scala_212, scala_213)
ThisBuild / tlBaseVersion := "0.23"
ThisBuild / developers += tlGitHubDev("rossabaker", "Ross A. Baker")

ThisBuild / tlCiReleaseBranches := Seq("series/0.23")
ThisBuild / tlSitePublishBranch := Some("series/0.23")

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbOptions ++= Seq("-P:semanticdb:synthetics:on").filter(_ => !tlIsScala3.value)
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"

ThisBuild / scalafixAll / skip := tlIsScala3.value
ThisBuild / ScalafixConfig / skip := tlIsScala3.value
ThisBuild / Test / scalafixConfig := Some(file(".scalafix.test.conf"))

ThisBuild / githubWorkflowBuild ++= Seq(
  WorkflowStep.Sbt(
    List("${{ matrix.ci }}", "scalafixAll --check"),
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
      )
    ),
    scalas = List(scala_213),
    javas = List(JavaSpec.temurin("8")),
  )
)

ThisBuild / jsEnv := {
  import org.scalajs.jsenv.nodejs.NodeJSEnv
  new NodeJSEnv(NodeJSEnv.Config().withEnv(Map("TZ" -> "UTC")))
}

lazy val modules: List[CompositeProject] = List(
  core,
  laws,
  testing,
  tests,
  server,
  client,
  clientTestkit,
  emberCore,
  emberServer,
  emberClient,
  blazeCore,
  blazeServer,
  blazeClient,
  asyncHttpClient,
  jettyClient,
  okHttpClient,
  theDsl,
  jawn,
  circe,
  bench,
  jsArtifactSizeTest,
  unidocs,
  examples,
  examplesBlaze,
  examplesDocker,
  examplesEmber,
  scalafixInternalRules,
  scalafixInternalInput,
  scalafixInternalOutput,
  scalafixInternalTests,
)

lazy val root = tlCrossRootProject
  .disablePlugins(ScalafixPlugin)
  .settings(
    // Root project
    name := "http4s",
    description := "A minimal, Scala-idiomatic library for HTTP",
    startYear := Some(2013),
  )
  .aggregate(modules: _*)

lazy val core = libraryCrossProject("core")
  .enablePlugins(BuildInfoPlugin)
  .jvmEnablePlugins(MimeLoaderPlugin)
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

      // MimeDB is a private trait (effectively sealed) so we can add abstract methods to it at will
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_0.org$http4s$MimeDB$application_parts$application_0$$_part_0"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_0.org$http4s$MimeDB$application_parts$application_0$$_part_0_="
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_1.org$http4s$MimeDB$application_parts$application_1$$_part_1"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_1.org$http4s$MimeDB$application_parts$application_1$$_part_1_="
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_2.org$http4s$MimeDB$application_parts$application_2$$_part_2"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_2.org$http4s$MimeDB$application_parts$application_2$$_part_2_="
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_3.org$http4s$MimeDB$application_parts$application_3$$_part_3"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.MimeDB#application_parts#application_3.org$http4s$MimeDB$application_parts$application_3$$_part_3_="
      ),
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
          ProblemFilters.exclude[MissingClassProblem](
            "org.http4s.syntax.LiteralsSyntax$urischeme$"
          ),
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.headers.Upgrade.parser"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.StaticFile.<clinit>"),
          ProblemFilters.exclude[ReversedMissingMethodProblem](
            "org.http4s.websocket.WebSocket.imapK"
          ),
          ProblemFilters.exclude[ReversedMissingMethodProblem](
            "org.http4s.MimeDB.org$http4s$MimeDB$$_allMediaTypes"
          ),
          ProblemFilters.exclude[ReversedMissingMethodProblem](
            "org.http4s.MimeDB.org$http4s$MimeDB$$_allMediaTypes_="
          ),
        )
      else Seq.empty
    },
  )
  .jvmSettings(
    libraryDependencies ++= {
      if (tlIsScala3.value) Seq.empty
      else
        Seq(
          slf4jApi // residual dependency from macros
        )
    }
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalaJavaLocalesEnUS.value,
      scalaJavaTime.value,
    ),
    jsVersionIntroduced("0.23.5"),
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
  .jsSettings(
    jsVersionIntroduced("0.23.5")
  )

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
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.server.middleware.CSRF.genTokenString"
          ),
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.server.middleware.authentication.Nonce.random"
          ),
        )
      else Nil
    },
  )
  .settings(BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(BuildInfoPlugin.buildInfoDefaultSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      BuildInfoKey.map(Test / resourceDirectory) { case (k, v) => k -> v.toString }
    ),
    buildInfoPackage := "org.http4s.server.test",
  )
  .jsSettings(
    jsVersionIntroduced("0.23.7")
  )
  .dependsOn(core, testing % "test->test", theDsl % "test->compile")

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
      // begin wsclient: initially private[http4s]
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.closeFrame"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.closeFrame"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.subprocotol"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.subprotocol"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.sendClose"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.sendClose$default$1$"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.client.websocket.WSConnectionHighLevel.sendClose$default$1"
      ),
      // end wsclient
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
  .jvmSettings(
    libraryDependencies ++= Seq(
      nettyBuffer % Test,
      nettyCodecHttp % Test,
    )
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5"),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.client.JavaNetClientBuilder"),
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.client.JavaNetClientBuilder$"),
    ),
  )
  .dependsOn(core, server % Test, testing % "test->test", theDsl % "test->compile")

lazy val clientTestkit = libraryCrossProject("client-testkit")
  .settings(
    description := "Client testkit for building http4s clients",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      munit.value,
      munitCatsEffect.value,
    ),
    mimaPreviousArtifacts := Set.empty,
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      nettyBuffer,
      nettyCodecHttp,
    )
  )
  .dependsOn(client, theDsl)

lazy val emberCore = libraryCrossProject("ember-core", CrossType.Full)
  .settings(
    description := "Base library for ember http4s clients and servers",
    startYear := Some(2019),
    unusedCompileDependenciesFilter -= moduleFilter("io.chrisdavenport", "log4cats-core"),
    libraryDependencies ++= Seq(
      log4catsCore.value,
      log4catsTesting.value % Test,
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
      ProblemFilters.exclude[MissingClassProblem]("org.http4s.ember.core.h2.HpackPlatform$Impl"),
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
          ProblemFilters.exclude[MissingTypesProblem]("org.http4s.ember.core.h2.Hpack$"),
          ProblemFilters.exclude[IncompatibleTemplateDefProblem](
            "org.http4s.ember.core.h2.HpackPlatform"
          ),
        )
      else Seq.empty
    },
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5"),
    mimaBinaryIssueFilters ++= {
      Seq(
        ProblemFilters.exclude[Problem]("org.http4s.ember.core.h2.facade.*")
      )
    },
    mimaBinaryIssueFilters ++= {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters.exclude[IncompatibleTemplateDefProblem](
            "org.http4s.ember.core.h2.facade.Compressor"
          ),
          ProblemFilters.exclude[IncompatibleTemplateDefProblem](
            "org.http4s.ember.core.h2.facade.Decompressor"
          ),
        )
      else
        Seq.empty
    },
  )
  .jvmSettings(
    libraryDependencies += twitterHpack
  )
  .jsSettings(
    libraryDependencies += hpack.value
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
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.ember.server.internal.ServerHelpers.server"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.ember.server.internal.ServerHelpers.upgradeSocket"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.ember.server.internal.ServerHelpers.serverInternal"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.ember.server.internal.ServerHelpers.unixSocketServer"
          ),
        )
      else
        Seq.empty
    },
    Test / parallelExecution := false,
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      log4catsSlf4j,
      javaWebSocket % Test,
      jnrUnixSocket % Test, // Necessary for jdk < 16
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      log4catsNoop.value
    ),
    jsVersionIntroduced("0.23.7"),
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
      keypool.value
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
    jsVersionIntroduced("0.23.5"),
  )
  .dependsOn(emberCore % "compile;test->test", client, clientTestkit % Test)

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
    description := "Base library for binding blaze to http4s clients and servers",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      blazeHttp
    ),
    mimaBinaryIssueFilters := {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.BodylessWriter.this"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.BodylessWriter.ec"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.EntityBodyWriter.ec"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.CachingChunkWriter.ec"),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.blazecore.util.CachingStaticWriter.this"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.blazecore.util.CachingStaticWriter.ec"
          ),
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.blazecore.util.FlushingChunkWriter.ec"
          ),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.Http2Writer.this"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.Http2Writer.ec"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.IdentityWriter.this"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blazecore.util.IdentityWriter.ec"),
        )
      else Seq.empty
    },
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
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.BlazeServerBuilder.this"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.WebSocketDecoder.this"),
    ) ++ {
      if (tlIsScala3.value)
        Seq(
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.Http1ServerStage.apply"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.Http1ServerStage.apply"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.ProtocolSelector.apply"),
          ProblemFilters
            .exclude[DirectMissingMethodProblem]("org.http4s.blaze.server.ProtocolSelector.apply"),
          ProblemFilters.exclude[ReversedMissingMethodProblem](
            "org.http4s.blaze.server.WebSocketSupport.maxBufferSize"
          ),
          ProblemFilters.exclude[ReversedMissingMethodProblem](
            "org.http4s.blaze.server.WebSocketSupport.webSocketKey"
          ),
        )
      else Seq.empty,
    },
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
  .dependsOn(blazeCore % "compile;test->test", client.jvm, clientTestkit.jvm % Test)

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
  .dependsOn(core.jvm, testing.jvm % "test->test", client.jvm, clientTestkit.jvm % Test)

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
  .dependsOn(core.jvm, testing.jvm % "test->test", client.jvm, clientTestkit.jvm % Test)

lazy val okHttpClient = libraryProject("okhttp-client")
  .settings(
    description := "okhttp implementation for http4s clients",
    startYear := Some(2018),
    libraryDependencies ++= Seq(
      Http4sPlugin.okhttp,
      okio,
    ),
  )
  .dependsOn(core.jvm, testing.jvm % "test->test", client.jvm, clientTestkit.jvm % Test)

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryCrossProject("dsl", CrossType.Pure)
  .settings(
    description := "Simple DSL for writing http4s services",
    startYear := Some(2013),
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5")
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
  .jsSettings(
    jsVersionIntroduced("0.23.5")
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
  .jsSettings(
    jsVersionIntroduced("0.23.5")
  )
  .jvmSettings(libraryDependencies += circeJawn.value)
  .jsSettings(libraryDependencies += circeJawn15.value)
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

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

lazy val jsArtifactSizeTest = http4sProject("js-artifact-size-test")
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .settings(
    // CI automatically links SJS test artifacts in a separate step, to avoid OOMs while running tests
    // By placing the app in Test scope it gets linked as part of that CI step
    Test / scalaJSUseMainModuleInitializer := true,
    Test / scalaJSUseTestModuleInitializer := false,
    Test / scalaJSStage := FullOptStage,
    Test / test := {
      val log = streams.value.log
      val file = (Test / fullOptJS).value.data
      val size = io.Using.fileInputStream(file) { in =>
        var size = 0L
        IO.gzip(in, _ => size += 1)
        size
      }
      val sizeKB = size / 1000
      // not a hard target. increase *moderately* if need be
      // linking MimeDB results in a 100 KB increase. don't let that happen :)
      val targetKB = 350
      val msg = s"fullOptJS+gzip generated ${sizeKB} KB artifact (target: <$targetKB KB)"
      if (sizeKB < targetKB)
        log.info(msg)
      else
        sys.error(msg)
    },
  )
  .dependsOn(client.js, circe.js)

lazy val unidocs = http4sProject("unidocs")
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    moduleName := "http4s-docs",
    description := "Unified API documentation for http4s",
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        (List[ProjectReference](
          bench,
          examples,
          examplesBlaze,
          examplesDocker,
          examplesEmber,
          exampleEmberServerH2,
          exampleEmberClientH2,
          scalafixInternalInput,
          scalafixInternalOutput,
          scalafixInternalRules,
          scalafixInternalTests,
          docs,
        ) ++ root.js.aggregate): _*
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
    client.jvm,
    core.jvm,
    theDsl.jvm,
    emberServer.jvm,
    emberClient.jvm,
    circe.jvm,
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
  )
  .dependsOn(server.jvm, theDsl.jvm, circe.jvm)

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
    scalacOptions -= "-Xfatal-warnings",
  )
  .dependsOn(emberServer.jvm, emberClient.jvm)

lazy val exampleEmberServerH2 = exampleJSProject("examples-ember-server-h2")
  .dependsOn(emberServer.js)
  .settings(
    scalacOptions -= "-Xfatal-warnings"
  )

lazy val exampleEmberClientH2 = exampleJSProject("examples-ember-client-h2")
  .dependsOn(emberClient.js)
  .settings(
    scalacOptions -= "-Xfatal-warnings"
  )

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

lazy val scalafixInternalRules = project
  .in(file("scalafix-internal/rules"))
  .disablePlugins(ScalafixPlugin)
  .settings(
    name := "http4s-scalafix-internal",
    mimaPreviousArtifacts := Set.empty,
    startYear := Some(2021),
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % _root_.scalafix.sbt.BuildInfo.scalafixVersion
    ).filter(_ => !tlIsScala3.value),
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
    .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
    .settings(
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    )
    .dependsOn(theDsl.js)

lazy val commonSettings = Seq(
  Compile / doc / scalacOptions += "-no-link-warnings",
  libraryDependencies ++= Seq(
    catsLaws.value,
    logbackClassic,
    scalacheck.value,
  ).map(_ % Test),
)

def jsVersionIntroduced(v: String) = Seq(
  tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> v).toMap
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

lazy val enableFatalWarnings: String =
  """set ThisBuild / scalacOptions += "-Xfatal-warnings""""

lazy val disableFatalWarnings: String =
  """set ThisBuild / scalacOptions -= "-Xfatal-warnings""""

addCommandAlias(
  "quicklint",
  List(
    enableFatalWarnings,
    "scalafixAll --triggered",
    "scalafixAll",
    "scalafmtAll",
    "scalafmtSbt",
    disableFatalWarnings,
  ).mkString(" ;"),
)

// Use this command for checking before submitting a PR
addCommandAlias(
  "lint",
  List(
    enableFatalWarnings,
    "clean",
    "+test:compile",
    "scalafixAll --triggered",
    "scalafixAll",
    "+scalafmtAll",
    "scalafmtSbt",
    "+mimaReportBinaryIssues",
    disableFatalWarnings,
  ).mkString(" ;"),
)
