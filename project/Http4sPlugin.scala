package org.http4s.sbt

import com.github.tkawachi.doctest.DoctestPlugin.autoImport._
import com.timushev.sbt.updates.UpdatesPlugin.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.git.JGit
import com.typesafe.tools.mima.plugin.MimaKeys._
import de.heikoseeberger.sbtheader.{License, LicenseStyle}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport.unusedCompileDependenciesFilter
import sbt.Keys._
import sbt._
import sbtghactions.GenerativeKeys._
import sbtspiewak.NowarnCompatPlugin.autoImport.nowarnCompatAnnotationProvider

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running on CI")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = Http4sOrgPlugin

  val scala_213 = "2.13.6"
  val scala_212 = "2.12.15"
  val scala_3 = "3.0.2"

  override lazy val globalSettings = Seq(
    isCi := sys.env.get("CI").isDefined
  )

  override lazy val buildSettings = Seq(
    // Many steps only run on one build. We distinguish the primary build from
    // secondary builds by the Travis build number.
    http4sApiVersion := version.map { case VersionNumber(Seq(major, minor, _*), _, _) =>
      (major.toInt, minor.toInt)
    }.value
  ) ++ sbtghactionsSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    headerSources / excludeFilter := HiddenFileFilter ||
      new FileFilter {
        def accept(file: File) =
          attributedSources.contains(baseDirectory.value.toPath.relativize(file.toPath).toString)

        val attributedSources = Set(
          "src/main/scala/org/http4s/CacheDirective.scala",
          "src/main/scala/org/http4s/Challenge.scala",
          "src/main/scala/org/http4s/Charset.scala",
          "src/main/scala/org/http4s/ContentCoding.scala",
          "src/main/scala/org/http4s/Credentials.scala",
          "src/main/scala/org/http4s/Header.scala",
          "src/main/scala/org/http4s/LanguageTag.scala",
          "src/main/scala/org/http4s/MediaType.scala",
          "src/main/scala/org/http4s/RangeUnit.scala",
          "src/main/scala/org/http4s/ResponseCookie.scala",
          "src/main/scala/org/http4s/TransferCoding.scala",
          "src/main/scala/org/http4s/Uri.scala",
          "src/main/scala/org/http4s/dsl/impl/Path.scala",
          "src/main/scala/org/http4s/ember/core/ChunkedEncoding.scala",
          "src/main/scala/org/http4s/internal/CharPredicate.scala",
          "src/main/scala/org/http4s/parser/AcceptCharsetHeader.scala",
          "src/main/scala/org/http4s/parser/AcceptEncodingHeader.scala",
          "src/main/scala/org/http4s/parser/AcceptHeader.scala",
          "src/main/scala/org/http4s/parser/AcceptLanguageHeader.scala",
          "src/main/scala/org/http4s/parser/AdditionalRules.scala",
          "src/main/scala/org/http4s/parser/AuthorizationHeader.scala",
          "src/main/scala/org/http4s/parser/CacheControlHeader.scala",
          "src/main/scala/org/http4s/parser/ContentTypeHeader.scala",
          "src/main/scala/org/http4s/parser/CookieHeader.scala",
          "src/main/scala/org/http4s/parser/HttpHeaderParser.scala",
          "src/main/scala/org/http4s/parser/Rfc2616BasicRules.scala",
          "src/main/scala/org/http4s/parser/SimpleHeaders.scala",
          "src/main/scala/org/http4s/parser/WwwAuthenticateHeader.scala",
          "src/main/scala/org/http4s/play/Parser.scala",
          "src/main/scala/org/http4s/util/UrlCoding.scala",
          "src/test/scala/org/http4s/Http4sSpec.scala",
          "src/test/scala/org/http4s/UriSpec.scala",
          "src/test/scala/org/http4s/dsl/PathSpec.scala",
          "src/test/scala/org/http4s/testing/ErrorReporting.scala",
        )
      },
    nowarnCompatAnnotationProvider := None,
    mimaPreviousArtifacts := {
      mimaPreviousArtifacts.value.filterNot(
        // cursed release
        _.revision == "0.21.10"
      )
    },
    doctestTestFramework := DoctestTestFramework.Munit,
  )

  def extractApiVersion(version: String) = {
    val VersionExtractor = """(\d+)\.(\d+)[-.].*""".r
    version match {
      case VersionExtractor(major, minor) => (major.toInt, minor.toInt)
    }
  }

  def extractDocsPrefix(version: String) =
    extractApiVersion(version).productIterator.mkString("/v", ".", "")

  /** @return the version we want to document, for example in mdoc,
    * given the version being built.
    *
    * For snapshots after a stable release, return the previous stable
    * release.  For snapshots of 0.16.0 and 0.17.0, return the latest
    * milestone.  Otherwise, just return the current version.
    */
  def docExampleVersion(currentVersion: String) = {
    val MilestoneVersionExtractor = """(0).(16|17).(0)a?-SNAPSHOT""".r
    val latestMilestone = "M1"
    val VersionExtractor = """(\d+)\.(\d+)\.(\d+).*""".r
    currentVersion match {
      case MilestoneVersionExtractor(major, minor, patch) =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt}-$latestMilestone"
      case VersionExtractor(major, minor, patch) if patch.toInt > 0 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt - 1}"
      case _ =>
        currentVersion
    }
  }

  def latestPerMinorVersion(file: File): Map[(Long, Long), VersionNumber] = {
    def majorMinor(v: VersionNumber) = v match {
      case VersionNumber(Seq(major, minor, _), _, _) =>
        Some((major, minor))
      case _ =>
        None
    }

    // M before RC before final
    def patchSortKey(v: VersionNumber) = v match {
      case VersionNumber(Seq(_, _, patch), Seq(q), _) if q.startsWith("M") =>
        (patch, 0L, q.drop(1).toLong)
      case VersionNumber(Seq(_, _, patch), Seq(q), _) if q.startsWith("RC") =>
        (patch, 1L, q.drop(2).toLong)
      case VersionNumber(Seq(_, _, patch), Seq(), _) => (patch, 2L, 0L)
      case _ => (-1L, -1L, -1L)
    }

    JGit(file).tags
      .collect {
        case ref if ref.getName.startsWith("refs/tags/v") =>
          VersionNumber(ref.getName.substring("refs/tags/v".size))
      }
      .foldLeft(Map.empty[(Long, Long), VersionNumber]) { case (m, v) =>
        majorMinor(v) match {
          case Some(key) =>
            val max =
              m.get(key).fold(v)(v0 => Ordering[(Long, Long, Long)].on(patchSortKey).max(v, v0))
            m.updated(key, max)
          case None => m
        }
      }
  }

  def docsProjectSettings: Seq[Setting[_]] =
    Seq(
      git.remoteRepo := "git@github.com:http4s/http4s.git"
    )

  def sbtghactionsSettings: Seq[Setting[_]] = {
    import sbtghactions.GenerativeKeys._
    import sbtghactions._

    def siteBuildJob(subproject: String, runMdoc: Boolean) = {
      val mdoc = if (runMdoc) Some(s"$subproject/mdoc") else None
      WorkflowJob(
        id = subproject,
        name = s"Build $subproject",
        scalas = List(scala_212),
        javas = List("adoptium@8"),
        steps = List(
          WorkflowStep.CheckoutFull,
          WorkflowStep.SetupScala,
          WorkflowStep.Sbt(
            mdoc.toList ++ List(s"$subproject/laikaSite"),
            name = Some(s"Build $subproject"),
          ),
        ),
      )
    }

    def sitePublishStep(subproject: String, runMdoc: Boolean) = {
      val mdoc = if (runMdoc) s"$subproject/mdoc " else ""
      WorkflowStep.Run(
        List(s"""
          |eval "$$(ssh-agent -s)"
          |echo "$$SSH_PRIVATE_KEY" | ssh-add -
          |git config --global user.name "GitHub Actions CI"
          |git config --global user.email "ghactions@invalid"
          |sbt ++$scala_212 $mdoc$subproject/laikaSite $subproject/ghpagesPushSite
          |
      """.stripMargin),
        name = Some(s"Publish $subproject"),
        env = Map("SSH_PRIVATE_KEY" -> "${{ secrets.SSH_PRIVATE_KEY }}"),
      )
    }

    Http4sOrgPlugin.githubActionsSettings ++ Seq(
      githubWorkflowBuild := Seq(
        WorkflowStep
          .Sbt(List("scalafmtCheckAll"), name = Some("Check formatting")),
        WorkflowStep.Sbt(List("headerCheck", "test:headerCheck"), name = Some("Check headers")),
        WorkflowStep.Sbt(List("test:compile"), name = Some("Compile")),
        WorkflowStep.Sbt(List("mimaReportBinaryIssues"), name = Some("Check binary compatibility")),
        WorkflowStep
          .Sbt(List("unusedCompileDependenciesTest"), name = Some("Check unused dependencies")),
        WorkflowStep.Sbt(List("test"), name = Some("Run tests")),
        WorkflowStep.Sbt(List("doc"), name = Some("Build docs")),
      ),
      githubWorkflowTargetBranches :=
        // "*" doesn't include slashes
        List("*", "series/*"),
      githubWorkflowPublishPreamble := {
        githubWorkflowPublishPreamble.value ++ Seq(
          WorkflowStep.Run(List("git status"))
        )
      },
      githubWorkflowPublishTargetBranches := Seq(
        RefPredicate.Equals(Ref.Branch("main")),
        RefPredicate.StartsWith(Ref.Tag("v")),
      ),
      githubWorkflowPublishPostamble := Seq(
        sitePublishStep("website", runMdoc = false)
        // sitePublishStep("docs", runMdoc = true)
      ),
      // this results in nonexistent directories trying to be compressed
      githubWorkflowArtifactUpload := false,
      githubWorkflowAddedJobs := Seq(
        siteBuildJob("website", runMdoc = false),
        siteBuildJob("docs", runMdoc = true),
      ),
    )
  }

  object V { // Dependency versions
    // We pull multiple modules from several projects. This is a convenient
    // reference of all the projects we depend on, and hopefully will reduce
    // error-prone merge conflicts in the dependencies below.
    val asyncHttpClient = "2.12.3"
    val blaze = "0.15.2"
    val boopickle = "1.4.0"
    val caseInsensitive = "1.2.0"
    val cats = "2.6.1"
    val catsEffect = "2.5.4"
    val catsParse = "0.3.4"
    val circe = "0.14.1"
    val crypto = "0.1.0"
    val cryptobits = "1.3"
    val disciplineCore = "1.2.0"
    val dropwizardMetrics = "4.2.4"
    val fs2 = "2.5.10"
    val ip4s = "2.0.4"
    val javaWebSocket = "1.5.2"
    val jawn = "1.2.0"
    val jawnFs2 = "1.1.3"
    val jetty = "9.4.44.v20210927"
    val keypool = "0.3.5"
    val literally = "1.0.2"
    val logback = "1.2.6"
    val log4cats = "1.3.1"
    val log4s = "1.10.0"
    val munit = "0.7.29"
    val munitCatsEffect = "1.0.6"
    val munitDiscipline = "1.0.9"
    val netty = "4.1.70.Final"
    val okio = "2.10.0"
    val okhttp = "4.9.2"
    val playJson = "2.9.2"
    val prometheusClient = "0.11.0"
    val reactiveStreams = "1.0.3"
    val quasiquotes = "2.1.0"
    val scalacheck = "1.15.4"
    val scalacheckEffect = "1.0.3"
    val scalatags = "0.10.0"
    val scalaXml = "2.0.1"
    val scodecBits = "1.1.29"
    val servlet = "3.1.0"
    val slf4j = "1.7.32"
    val tomcat = "9.0.54"
    val treehugger = "0.4.4"
    val twirl = "1.4.2"
    val vault = "2.1.13"
  }

  lazy val asyncHttpClient = "org.asynchttpclient" % "async-http-client" % V.asyncHttpClient
  lazy val blazeCore = "org.http4s" %% "blaze-core" % V.blaze
  lazy val blazeHttp = "org.http4s" %% "blaze-http" % V.blaze
  lazy val boopickle = "io.suzaku" %% "boopickle" % V.boopickle
  lazy val caseInsensitive = "org.typelevel" %% "case-insensitive" % V.caseInsensitive
  lazy val caseInsensitiveTesting =
    "org.typelevel" %% "case-insensitive-testing" % V.caseInsensitive
  lazy val catsCore = "org.typelevel" %% "cats-core" % V.cats
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
  lazy val catsEffectLaws = "org.typelevel" %% "cats-effect-laws" % V.catsEffect
  lazy val catsLaws = "org.typelevel" %% "cats-laws" % V.cats
  lazy val catsParse = "org.typelevel" %% "cats-parse" % V.catsParse
  lazy val circeCore = "io.circe" %% "circe-core" % V.circe
  lazy val circeGeneric = "io.circe" %% "circe-generic" % V.circe
  lazy val circeJawn = "io.circe" %% "circe-jawn" % V.circe
  lazy val circeLiteral = "io.circe" %% "circe-literal" % V.circe
  lazy val circeParser = "io.circe" %% "circe-parser" % V.circe
  lazy val circeTesting = "io.circe" %% "circe-testing" % V.circe
  lazy val crypto = "org.http4s" %% "http4s-crypto" % V.crypto
  lazy val cryptobits = "org.reactormonk" %% "cryptobits" % V.cryptobits
  lazy val disciplineCore = "org.typelevel" %% "discipline-core" % V.disciplineCore
  lazy val dropwizardMetricsCore = "io.dropwizard.metrics" % "metrics-core" % V.dropwizardMetrics
  lazy val dropwizardMetricsJson = "io.dropwizard.metrics" % "metrics-json" % V.dropwizardMetrics
  lazy val fs2Core = "co.fs2" %% "fs2-core" % V.fs2
  lazy val fs2Io = "co.fs2" %% "fs2-io" % V.fs2
  lazy val fs2ReactiveStreams = "co.fs2" %% "fs2-reactive-streams" % V.fs2
  lazy val ip4sCore = "com.comcast" %% "ip4s-core" % V.ip4s
  lazy val ip4sTestKit = "com.comcast" %% "ip4s-test-kit" % V.ip4s
  lazy val javaxServletApi = "javax.servlet" % "javax.servlet-api" % V.servlet
  lazy val javaWebSocket = "org.java-websocket" % "Java-WebSocket" % V.javaWebSocket
  lazy val jawnFs2 = "org.http4s" %% "jawn-fs2" % V.jawnFs2
  lazy val jawnParser = "org.typelevel" %% "jawn-parser" % V.jawn
  lazy val jawnPlay = "org.typelevel" %% "jawn-play" % V.jawn
  lazy val jettyClient = "org.eclipse.jetty" % "jetty-client" % V.jetty
  lazy val jettyHttp = "org.eclipse.jetty" % "jetty-http" % V.jetty
  lazy val jettyHttp2Server = "org.eclipse.jetty.http2" % "http2-server" % V.jetty
  lazy val jettyRunner = "org.eclipse.jetty" % "jetty-runner" % V.jetty
  lazy val jettyServer = "org.eclipse.jetty" % "jetty-server" % V.jetty
  lazy val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % V.jetty
  lazy val jettyUtil = "org.eclipse.jetty" % "jetty-util" % V.jetty
  lazy val keypool = "org.typelevel" %% "keypool" % V.keypool
  lazy val literally = "org.typelevel" %% "literally" % V.literally
  lazy val log4catsCore = "org.typelevel" %% "log4cats-core" % V.log4cats
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % V.log4cats
  lazy val log4catsTesting = "org.typelevel" %% "log4cats-testing" % V.log4cats
  lazy val log4s = "org.log4s" %% "log4s" % V.log4s
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % V.logback
  lazy val munit = "org.scalameta" %% "munit" % V.munit
  lazy val munitCatsEffect = "org.typelevel" %% "munit-cats-effect-2" % V.munitCatsEffect
  lazy val munitDiscipline = "org.typelevel" %% "discipline-munit" % V.munitDiscipline
  lazy val nettyBuffer = "io.netty" % "netty-buffer" % V.netty
  lazy val nettyCodecHttp = "io.netty" % "netty-codec-http" % V.netty
  lazy val okio = "com.squareup.okio" % "okio" % V.okio
  lazy val okhttp = "com.squareup.okhttp3" % "okhttp" % V.okhttp
  lazy val playJson = "com.typesafe.play" %% "play-json" % V.playJson
  lazy val prometheusClient = "io.prometheus" % "simpleclient" % V.prometheusClient
  lazy val prometheusCommon = "io.prometheus" % "simpleclient_common" % V.prometheusClient
  lazy val prometheusHotspot = "io.prometheus" % "simpleclient_hotspot" % V.prometheusClient
  lazy val reactiveStreams = "org.reactivestreams" % "reactive-streams" % V.reactiveStreams
  lazy val quasiquotes = "org.scalamacros" %% "quasiquotes" % V.quasiquotes
  lazy val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  lazy val scalacheckEffect = "org.typelevel" %% "scalacheck-effect" % V.scalacheckEffect
  lazy val scalacheckEffectMunit = "org.typelevel" %% "scalacheck-effect-munit" % V.scalacheckEffect
  def scalaReflect(sv: String) = "org.scala-lang" % "scala-reflect" % sv
  lazy val scalatagsApi = "com.lihaoyi" %% "scalatags" % V.scalatags
  lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % V.scalaXml
  lazy val scodecBits = "org.scodec" %% "scodec-bits" % V.scodecBits
  lazy val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j
  lazy val tomcatCatalina = "org.apache.tomcat" % "tomcat-catalina" % V.tomcat
  lazy val tomcatCoyote = "org.apache.tomcat" % "tomcat-coyote" % V.tomcat
  lazy val tomcatUtilScan = "org.apache.tomcat" % "tomcat-util-scan" % V.tomcat
  lazy val treeHugger = "com.eed3si9n" %% "treehugger" % V.treehugger
  lazy val twirlApi = "com.typesafe.play" %% "twirl-api" % V.twirl
  lazy val vault = "org.typelevel" %% "vault" % V.vault
}
