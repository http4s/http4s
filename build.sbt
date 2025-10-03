import com.typesafe.tools.mima.core._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import org.http4s.sbt.Http4sPlugin._

import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
ThisBuild / crossScalaVersions := Seq(scala_3, scala_212, scala_213)
ThisBuild / tlBspCrossProjectPlatforms := Set(JVMPlatform)
ThisBuild / tlBaseVersion := "0.23"
ThisBuild / developers += tlGitHubDev("rossabaker", "Ross A. Baker")

ThisBuild / tlCiReleaseBranches := Seq("series/0.23")
ThisBuild / tlSitePublishBranch := Some("series/0.23")

ThisBuild / scalafixAll / skip := tlIsScala3.value
ThisBuild / ScalafixConfig / skip := tlIsScala3.value
ThisBuild / Test / scalafixConfig := Some(file(".scalafix.test.conf"))

ThisBuild / githubWorkflowJobSetup ~= { steps =>
  Seq(
    WorkflowStep.Use(
      UseRef.Public("cachix", "install-nix-action", "v27"),
      name = Some("Install Nix"),
    )
  ) ++ steps
}

ThisBuild / githubWorkflowSbtCommand := "nix develop .#${{ matrix.java }} -c sbt"

ThisBuild / githubWorkflowArtifactUpload := false

ThisBuild / jsEnv := {
  import org.scalajs.jsenv.nodejs.NodeJSEnv
  new NodeJSEnv(
    NodeJSEnv.Config().withEnv(Map("TZ" -> "UTC")).withArgs(List("--max-old-space-size=512"))
  )
}

lazy val modules: List[CompositeProject] = List(
  core,
  laws,
  tests,
  server,
  client,
  clientTestkit,
  emberCore,
  emberServer,
  emberClient,
  theDsl,
  jawn,
  circe,
  bench,
  jsArtifactSizeTest,
  unidocs,
  examples,
  examplesDocker,
  examplesEmber,
  scalafixInternalRules,
  scalafixInternalInput,
  scalafixInternalOutput,
  scalafixInternalTests,
)

lazy val root = tlCrossRootProject
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
      catsFree.value,
      catsParse.value.exclude("org.typelevel", "cats-core_2.13"),
      crypto.value,
      fs2Core.value,
      fs2Io.value,
      ip4sCore.value,
      literally.value,
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

      // package-private, and only broken on JS/Native
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.Charset.availableCharsets"),
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
          ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.Message.logger"),
        )
      else Seq.empty
    },
  )
  .platformsSettings(JVMPlatform, JSPlatform)(
    libraryDependencies ++= Seq(
      log4s.value
    )
  )
  .platformsSettings(JSPlatform, NativePlatform)(
    libraryDependencies ++= Seq(
      log4catsNoop.value,
      scalaJavaLocalesEnUS.value,
      scalaJavaTime.value,
    )
  )
  .jvmSettings(
    libraryDependencies ++=
      Seq(log4catsSlf4j),
    libraryDependencies ++= {
      if (tlIsScala3.value) Seq.empty
      else
        Seq(
          slf4jApi // residual dependency from macros
        )
    },
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5"),
    libraryDependencies ++= Seq(log4catsJSConsole.value),
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

// Also defines shared test utils in Compile scope
lazy val tests = libraryCrossProject("tests")
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Tests for core project",
    startYear := Some(2013),
    libraryDependencies ++= Seq(
      munitCatsEffect.value,
      munitDiscipline.value,
      scalacheck.value,
      scalacheckEffect.value,
      scalacheckEffectMunit.value,
    ),
    githubWorkflowArtifactUpload := false,
  )
  .dependsOn(core, laws)

lazy val server = libraryCrossProject("server")
  .settings(
    description := "Base library for building http4s servers",
    startYear := Some(2014),
    Test / scalacOptions -= "-Xsource:3", // bugged
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
        "org.http4s.server.middleware.Metrics$MetricsRequestContext"
      ), // private
      ProblemFilters.exclude[MissingClassProblem](
        "org.http4s.server.middleware.Metrics$MetricsRequestContext$"
      ),
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
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.server.package.messageFailureLogger"
          ),
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.server.package.serviceErrorLogger"
          ),
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.server.middleware.CORS.logger"
          ),
          ProblemFilters.exclude[IncompatibleResultTypeProblem](
            "org.http4s.server.middleware.HttpsRedirect.logger"
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
  .dependsOn(core, tests % Test, theDsl % Test)

lazy val client = libraryCrossProject("client")
  .settings(
    description := "Base library for building http4s clients",
    startYear := Some(2014),
    Test / scalacOptions -= "-Xsource:3", // bugged
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
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.client.JavaNetClientBuilder.F"
      ), // sealed protected
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.client.JavaNetClientBuilder.this"
      ), // private
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
  .dependsOn(core, server % Test, tests % Test, theDsl % Test)

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
  .dependsOn(client, theDsl, server, tests % Test)

lazy val emberCore = libraryCrossProject("ember-core", CrossType.Full)
  .settings(
    description := "Base library for ember http4s clients and servers",
    startYear := Some(2019),
    unusedCompileDependenciesFilter -= moduleFilter("io.chrisdavenport", "log4cats-core"),
    libraryDependencies ++= Seq(
      log4catsCore.value,
      log4catsTesting.value % Test,
      log4catsNoop.value % Test,
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
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.core.Parser#MessageP.recurseFind"
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
      ProblemFilters.exclude[IncompatibleTemplateDefProblem](
        "org.http4s.ember.core.h2.HpackPlatform"
      ),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.ember.core.h2.H2Frame#*.type"
      ),
      ProblemFilters.exclude[MissingTypesProblem]("org.http4s.ember.core.h2.H2Frame$*"),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.core.h2.H2Frame#Ping.empty"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "org.http4s.ember.core.h2.H2Frame#Ping.emptyBV"
      ),
      ProblemFilters.exclude[Problem]("org.http4s.ember.core.h2.H2Client*"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.ember.core.h2.Hpack#Impl.this"
      ),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.ember.core.h2.H2Stream#State.readBuffer"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.ember.core.h2.H2Stream#State.copy"
      ),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.ember.core.h2.H2Stream#State.copy$default$8"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.ember.core.h2.H2Stream#State.this"
      ),
      ProblemFilters.exclude[IncompatibleMethTypeProblem](
        "org.http4s.ember.core.h2.H2Stream#State.apply"
      ),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "org.http4s.ember.core.h2.H2Stream#State._8"
      ),
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
          ProblemFilters.exclude[DirectMissingMethodProblem](
            "org.http4s.ember.core.h2.H2Frame#*.toRaw"
          ),
        )
      else Seq.empty
    },
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5"),
    mimaBinaryIssueFilters ++=
      Seq(
        ProblemFilters.exclude[Problem]("org.http4s.ember.core.h2.facade.*")
      ),
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
  .platformsSettings(JSPlatform, NativePlatform)(
    libraryDependencies += hpack.value
  )
  .dependsOn(core, tests % Test)

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
      ProblemFilters.exclude[Problem](
        "org.http4s.ember.server.internal.*"
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
        .exclude[DirectMissingMethodProblem]("org.http4s.ember.client.EmberClientBuilder.this"),
      ProblemFilters.exclude[Problem]("org.http4s.ember.client.internal.*"),
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

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryCrossProject("dsl", CrossType.Pure)
  .settings(
    description := "Simple DSL for writing http4s services",
    startYear := Some(2013),
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5")
  )
  .dependsOn(core, tests % Test)

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
  .dependsOn(core, tests % Test)

lazy val circe = libraryCrossProject("circe", CrossType.Pure)
  .settings(
    description := "Provides Circe codecs for http4s",
    startYear := Some(2015),
    libraryDependencies ++= Seq(
      circeCore.value,
      circeJawn.value,
      circeTesting.value % Test,
    ),
  )
  .jsSettings(
    jsVersionIntroduced("0.23.5")
  )
  .dependsOn(core, tests % Test, jawn % "compile;test->test")

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(
    description := "Benchmarks for http4s",
    startYear := Some(2015),
    libraryDependencies += circeParser,
    undeclaredCompileDependenciesTest := {},
    unusedCompileDependenciesTest := {},
    coverageEnabled := false,
  )
  .dependsOn(core.jvm, circe.jvm, emberCore.jvm)

lazy val jsArtifactSizeTest = http4sProject("js-artifact-size-test")
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .settings(
    startYear := Some(2022),
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
      // linking java.time.* results in a 70 KB increase
      val targetKB = 280
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
    startYear := Some(2022),
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        (List[ProjectReference](
          bench,
          examples,
          examplesDocker,
          examplesEmber,
          scalafixInternalInput,
          scalafixInternalOutput,
          scalafixInternalRules,
          scalafixInternalTests,
          docs,
        ) ++ root.js.aggregate ++ root.native.aggregate): _*
      ),
    coverageEnabled := false,
  )

lazy val docs = http4sProject("site")
  .enablePlugins(Http4sSitePlugin)
  .settings(
    libraryDependencies ++= Seq(
      circeGeneric,
      circeLiteral,
      cryptobits,
      jnrUnixSocket,
    ),
    description := "Documentation for http4s",
    tlFatalWarnings := false,
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
    coverageEnabled := false,
  )
  .dependsOn(server.jvm, theDsl.jvm, circe.jvm)

lazy val examplesEmber = exampleProject("examples-ember")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on ember",
    startYear := Some(2020),
    fork := true,
    tlFatalWarnings := false,
    coverageEnabled := false,
  )
  .dependsOn(emberServer.jvm, emberClient.jvm)

lazy val examplesDocker = http4sProject("examples-docker")
  .in(file("examples/docker"))
  .enablePlugins(JavaAppPackaging, DockerPlugin, NoPublishPlugin)
  .settings(
    description := "Builds a docker image for a ember-server",
    startYear := Some(2017),
    Docker / packageName := "http4s/ember-server",
    Docker / maintainer := "http4s",
    dockerUpdateLatest := true,
    dockerExposedPorts := List(8080),
    coverageEnabled := false,
  )
  .dependsOn(emberServer.jvm, theDsl.jvm)

lazy val scalafixInternalSettings = Seq(
  unusedCompileDependenciesFilter -= moduleFilter("org.typelevel", "scalac-compat-annotation")
)

lazy val scalafixInternalRules = project
  .in(file("scalafix-internal/rules"))
  .disablePlugins(ScalafixPlugin)
  .settings(scalafixInternalSettings)
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
  .settings(scalafixInternalSettings)
  .settings(
    startYear := Some(2022),
    headerSources / excludeFilter := AllPassFilter,
    tlFatalWarnings := false,
    semanticdbOptions ++= Seq("-P:semanticdb:synthetics:on").filter(_ => !tlIsScala3.value),
  )
  .dependsOn(core.jvm)

lazy val scalafixInternalOutput = project
  .in(file("scalafix-internal/output"))
  .enablePlugins(NoPublishPlugin)
  .disablePlugins(ScalafixPlugin)
  .settings(scalafixInternalSettings)
  .settings(
    startYear := Some(2022),
    headerSources / excludeFilter := AllPassFilter,
    tlFatalWarnings := false,
  )
  .dependsOn(core.jvm)

lazy val scalafixInternalTests = project
  .in(file("scalafix-internal/tests"))
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(ScalafixTestkitPlugin)
  .settings(
    startYear := Some(2021),
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
  .settings(scalafixInternalSettings)
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
    .CrossProject(name, file(name))(JVMPlatform, JSPlatform, NativePlatform)
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
    .nativeEnablePlugins(ScalaNativeBrewedConfigPlugin)
    .nativeSettings(
      tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.23.16").toMap,
      Test / nativeBrewFormulas ++= {
        if (sys.env.contains("DEVSHELL_DIR")) Set.empty else Set("s2n")
      },
      Test / envVars += "S2N_DONT_MLOCK" -> "1",
    )
    .enablePlugins(Http4sPlugin)
    .configurePlatforms(JSPlatform, NativePlatform)(_.disablePlugins(DoctestPlugin))
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

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    catsLaws.value,
    logbackClassic,
    scalacheck.value,
  ).map(_ % Test)
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
