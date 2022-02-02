package org.http4s.sbt

import com.github.tkawachi.doctest.DoctestPlugin.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.git.JGit
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import org.typelevel.sbt.gha.GenerativeKeys._
import org.typelevel.sbt.gha.JavaSpec

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running on CI")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = Http4sOrgPlugin

  val scala_213 = "2.13.8"
  val scala_212 = "2.12.15"
  val scala_3 = "3.1.1"

  override lazy val globalSettings = Seq(
    isCi := sys.env.contains("CI")
  )

  override lazy val buildSettings = Seq(
    // Many steps only run on one build. We distinguish the primary build from
    // secondary builds by the Travis build number.
    http4sApiVersion := version.map { case VersionNumber(Seq(major, minor, _*), _, _) =>
      (major.toInt, minor.toInt)
    }.value
  ) ++ sbtghactionsSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    headerSources / excludeFilter := HiddenFileFilter,
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
    import org.typelevel.sbt.gha.GenerativeKeys._
    import org.typelevel.sbt.gha._

    def siteBuildJob(subproject: String, runMdoc: Boolean) = {
      val mdoc = if (runMdoc) Some(s"$subproject/mdoc") else None
      WorkflowJob(
        id = subproject,
        name = s"Build $subproject",
        scalas = List(scala_212),
        javas = List(JavaSpec.temurin("17")),
        steps = List(
          WorkflowStep.CheckoutFull,
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

    Seq(
      githubWorkflowPublishPreamble := {
        githubWorkflowPublishPreamble.value ++ Seq(
          WorkflowStep.Run(List("git status"))
        )
      },
      githubWorkflowPublishPostamble := Seq(
        sitePublishStep("website", runMdoc = false)
        // sitePublishStep("docs", runMdoc = true)
      ),
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
    val blaze = "0.15.3"
    val boopickle = "1.4.0"
    val caseInsensitive = "1.2.0"
    val cats = "2.7.0"
    val catsEffect = "3.3.4"
    val catsParse = "0.3.6"
    val circe = "0.15.0-M1"
    val crypto = "0.2.0"
    val cryptobits = "1.3"
    val disciplineCore = "1.4.0"
    val dropwizardMetrics = "4.2.7"
    val fs2 = "3.2.4"
    val ip4s = "3.1.2"
    val javaWebSocket = "1.5.2"
    val jawn = "1.3.2"
    val jawnFs2 = "2.2.0"
    val jetty = "9.4.44.v20210927"
    val jnrUnixSocket = "0.38.17"
    val keypool = "0.4.7"
    val literally = "1.0.2"
    val logback = "1.2.6"
    val log4cats = "2.2.0"
    val log4s = "1.10.0"
    val munit = "0.7.29"
    val munitCatsEffect = "1.0.7"
    val munitDiscipline = "1.0.9"
    val netty = "4.1.73.Final"
    val okio = "2.10.0"
    val okhttp = "4.9.3"
    val playJson = "2.9.2"
    val prometheusClient = "0.14.1"
    val reactiveStreams = "1.0.3"
    val quasiquotes = "2.1.0"
    val scalacheck = "1.15.4"
    val scalacheckEffect = "1.0.3"
    val scalaJavaLocales = "1.3.0"
    val scalaJavaTime = "2.3.0"
    val scalatags = "0.11.1"
    val scalaXml = "2.0.1"
    val scodecBits = "1.1.30"
    val servlet = "3.1.0"
    val slf4j = "1.7.33"
    val tomcat = "9.0.58"
    val treehugger = "0.4.4"
    val twirl = "1.4.2"
    val vault = "3.1.0"
  }

  lazy val asyncHttpClient = "org.asynchttpclient" % "async-http-client" % V.asyncHttpClient
  lazy val blazeCore = "org.http4s" %% "blaze-core" % V.blaze
  lazy val blazeHttp = "org.http4s" %% "blaze-http" % V.blaze
  lazy val boopickle = Def.setting("io.suzaku" %%% "boopickle" % V.boopickle)
  lazy val caseInsensitive = Def.setting("org.typelevel" %%% "case-insensitive" % V.caseInsensitive)
  lazy val caseInsensitiveTesting =
    Def.setting("org.typelevel" %%% "case-insensitive-testing" % V.caseInsensitive)
  lazy val catsCore = Def.setting("org.typelevel" %%% "cats-core" % V.cats)
  lazy val catsEffect = Def.setting("org.typelevel" %%% "cats-effect" % V.catsEffect)
  lazy val catsEffectStd = Def.setting("org.typelevel" %%% "cats-effect-std" % V.catsEffect)
  lazy val catsEffectLaws = Def.setting("org.typelevel" %%% "cats-effect-laws" % V.catsEffect)
  lazy val catsEffectTestkit = Def.setting("org.typelevel" %%% "cats-effect-testkit" % V.catsEffect)
  lazy val catsLaws = Def.setting("org.typelevel" %%% "cats-laws" % V.cats)
  lazy val catsParse = Def.setting("org.typelevel" %%% "cats-parse" % V.catsParse)
  lazy val circeCore = Def.setting("io.circe" %%% "circe-core" % V.circe)
  lazy val circeGeneric = "io.circe" %% "circe-generic" % V.circe
  lazy val circeJawn = Def.setting("io.circe" %%% "circe-jawn" % V.circe)
  lazy val circeLiteral = "io.circe" %% "circe-literal" % V.circe
  lazy val circeParser = "io.circe" %% "circe-parser" % V.circe
  lazy val circeTesting = Def.setting("io.circe" %%% "circe-testing" % V.circe)
  lazy val crypto = Def.setting("org.http4s" %%% "http4s-crypto" % V.crypto)
  lazy val cryptobits = "org.reactormonk" %% "cryptobits" % V.cryptobits
  lazy val disciplineCore = Def.setting("org.typelevel" %%% "discipline-core" % V.disciplineCore)
  lazy val dropwizardMetricsCore = "io.dropwizard.metrics" % "metrics-core" % V.dropwizardMetrics
  lazy val dropwizardMetricsJson = "io.dropwizard.metrics" % "metrics-json" % V.dropwizardMetrics
  lazy val fs2Core = Def.setting("co.fs2" %%% "fs2-core" % V.fs2)
  lazy val fs2Io = Def.setting("co.fs2" %%% "fs2-io" % V.fs2)
  lazy val fs2ReactiveStreams = "co.fs2" %% "fs2-reactive-streams" % V.fs2
  lazy val ip4sCore = Def.setting("com.comcast" %%% "ip4s-core" % V.ip4s)
  lazy val ip4sTestKit = Def.setting("com.comcast" %%% "ip4s-test-kit" % V.ip4s)
  lazy val javaxServletApi = "javax.servlet" % "javax.servlet-api" % V.servlet
  lazy val jawnFs2 = Def.setting("org.typelevel" %%% "jawn-fs2" % V.jawnFs2)
  lazy val javaWebSocket = "org.java-websocket" % "Java-WebSocket" % V.javaWebSocket
  lazy val jawnParser = Def.setting("org.typelevel" %%% "jawn-parser" % V.jawn)
  lazy val jawnPlay = "org.typelevel" %% "jawn-play" % V.jawn
  lazy val jettyClient = "org.eclipse.jetty" % "jetty-client" % V.jetty
  lazy val jettyHttp = "org.eclipse.jetty" % "jetty-http" % V.jetty
  lazy val jettyHttp2Server = "org.eclipse.jetty.http2" % "http2-server" % V.jetty
  lazy val jettyRunner = "org.eclipse.jetty" % "jetty-runner" % V.jetty
  lazy val jettyServer = "org.eclipse.jetty" % "jetty-server" % V.jetty
  lazy val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % V.jetty
  lazy val jettyUtil = "org.eclipse.jetty" % "jetty-util" % V.jetty
  lazy val jnrUnixSocket = "com.github.jnr" % "jnr-unixsocket" % V.jnrUnixSocket
  lazy val keypool = Def.setting("org.typelevel" %%% "keypool" % V.keypool)
  lazy val literally = Def.setting("org.typelevel" %%% "literally" % V.literally)
  lazy val log4catsCore = Def.setting("org.typelevel" %%% "log4cats-core" % V.log4cats)
  lazy val log4catsNoop = Def.setting("org.typelevel" %%% "log4cats-noop" % V.log4cats)
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % V.log4cats
  lazy val log4catsTesting = Def.setting("org.typelevel" %%% "log4cats-testing" % V.log4cats)
  lazy val log4s = Def.setting("org.log4s" %%% "log4s" % V.log4s)
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % V.logback
  lazy val munit = Def.setting("org.scalameta" %%% "munit" % V.munit)
  lazy val munitCatsEffect =
    Def.setting("org.typelevel" %%% "munit-cats-effect-3" % V.munitCatsEffect)
  lazy val munitDiscipline = Def.setting("org.typelevel" %%% "discipline-munit" % V.munitDiscipline)
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
  lazy val scalacheck = Def.setting("org.scalacheck" %%% "scalacheck" % V.scalacheck)
  lazy val scalacheckEffect =
    Def.setting("org.typelevel" %%% "scalacheck-effect" % V.scalacheckEffect)
  lazy val scalacheckEffectMunit =
    Def.setting("org.typelevel" %%% "scalacheck-effect-munit" % V.scalacheckEffect)
  lazy val scalaJavaLocalesEnUS =
    Def.setting("io.github.cquiroz" %%% "locales-minimal-en_us-db" % V.scalaJavaLocales)
  lazy val scalaJavaTime = Def.setting("io.github.cquiroz" %%% "scala-java-time" % V.scalaJavaTime)
  lazy val scalaJavaTimeTzdb =
    Def.setting("io.github.cquiroz" %%% "scala-java-time-tzdb" % V.scalaJavaTime)
  def scalaReflect(sv: String) = "org.scala-lang" % "scala-reflect" % sv
  lazy val scalatagsApi = "com.lihaoyi" %% "scalatags" % V.scalatags
  lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % V.scalaXml
  lazy val scodecBits = Def.setting("org.scodec" %%% "scodec-bits" % V.scodecBits)
  lazy val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j
  lazy val tomcatCatalina = "org.apache.tomcat" % "tomcat-catalina" % V.tomcat
  lazy val tomcatCoyote = "org.apache.tomcat" % "tomcat-coyote" % V.tomcat
  lazy val tomcatUtilScan = "org.apache.tomcat" % "tomcat-util-scan" % V.tomcat
  lazy val treeHugger = "com.eed3si9n" %% "treehugger" % V.treehugger
  lazy val twirlApi = "com.typesafe.play" %% "twirl-api" % V.twirl
  lazy val vault = Def.setting("org.typelevel" %%% "vault" % V.vault)
}
