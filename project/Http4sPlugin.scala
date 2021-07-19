package org.http4s.sbt

import com.timushev.sbt.updates.UpdatesPlugin.autoImport._ // autoImport vs. UpdateKeys necessary here for implicit
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.git.JGit
import com.typesafe.tools.mima.plugin.MimaKeys._
import de.heikoseeberger.sbtheader.{License, LicenseStyle}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport.unusedCompileDependenciesFilter
import sbt.Keys._
import sbt._
import sbtspiewak.NowarnCompatPlugin.autoImport.nowarnCompatAnnotationProvider
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running on CI")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
    val http4sBuildData = taskKey[Unit]("Export build metadata for Hugo")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = Http4sOrgPlugin

  val scala_213 = "2.13.6"
  val scala_212 = "2.12.13"
  val scala_3 = "3.0.0"

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
    http4sBuildData := {
      val dest = target.value / "hugo-data" / "build.toml"
      val (major, minor) = http4sApiVersion.value

      val releases = latestPerMinorVersion(baseDirectory.value)
        .map { case ((major, minor), v) => s""""$major.$minor" = "${v.toString}"""" }
        .mkString("\n")

      // Would be more elegant if `[versions.http4s]` was nested, but then
      // the index lookups in `shortcodes/version.html` get complicated.
      val buildData: String =
        s"""
           |[versions]
           |"http4s.api" = "$major.$minor"
           |"http4s.current" = "${version.value}"
           |"http4s.doc" = "${docExampleVersion(version.value)}"
           |circe = "${circeJawn.value.revision}"
           |cryptobits = "${cryptobits.value.revision}"
           |
           |[releases]
           |$releases
         """.stripMargin

      IO.write(dest, buildData)
    },
    // servlet-4.0 is not yet supported by jetty-9 or tomcat-9, so don't accidentally depend on its new features
    dependencyUpdatesFilter -= moduleFilter(organization = "javax.servlet", revision = "4.0.0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "javax.servlet", revision = "4.0.1"),
    // servlet containers skipped until we figure out our Jakarta EE strategy
    dependencyUpdatesFilter -= moduleFilter(
      organization = "org.eclipse.jetty*",
      revision = "10.0.*"),
    dependencyUpdatesFilter -= moduleFilter(
      organization = "org.eclipse.jetty*",
      revision = "11.0.*"),
    dependencyUpdatesFilter -= moduleFilter(
      organization = "org.apache.tomcat",
      revision = "10.0.*"),
    // Cursed release. Calls ByteBuffer incompatibly with JDK8
    dependencyUpdatesFilter -= moduleFilter(name = "boopickle", revision = "1.3.2"),
    headerSources / excludeFilter := HiddenFileFilter ||
      new FileFilter {
        def accept(file: File) =
          attributedSources.contains(baseDirectory.value.toPath.relativize(file.toPath).toString)

        val attributedSources = Set(
          "shared/src/main/scala/org/http4s/CacheDirective.scala",
          "shared/src/main/scala/org/http4s/Challenge.scala",
          "shared/src/main/scala/org/http4s/Charset.scala",
          "shared/src/main/scala/org/http4s/ContentCoding.scala",
          "shared/src/main/scala/org/http4s/Credentials.scala",
          "shared/src/main/scala/org/http4s/Header.scala",
          "shared/src/main/scala/org/http4s/LanguageTag.scala",
          "shared/src/main/scala/org/http4s/MediaType.scala",
          "shared/src/main/scala/org/http4s/RangeUnit.scala",
          "shared/src/main/scala/org/http4s/ResponseCookie.scala",
          "shared/src/main/scala/org/http4s/TransferCoding.scala",
          "shared/src/main/scala/org/http4s/Uri.scala",
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
          "src/test/scala/org/http4s/testing/ErrorReporting.scala"
        )
      },
    nowarnCompatAnnotationProvider := None,
    mimaPreviousArtifacts := {
      mimaPreviousArtifacts.value.filterNot(
        // cursed release
        _.revision == "0.21.10"
      )
    }
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

  def docsProjectSettings: Seq[Setting[_]] = {
    import com.typesafe.sbt.site.hugo.HugoPlugin.autoImport._
    Seq(
      git.remoteRepo := "git@github.com:http4s/http4s.git",
      Hugo / includeFilter := (
        "*.html" | "*.png" | "*.jpg" | "*.gif" | "*.ico" | "*.svg" |
          "*.js" | "*.swf" | "*.json" | "*.md" |
          "*.css" | "*.woff" | "*.woff2" | "*.ttf" |
          "CNAME" | "_config.yml" | "_redirects"
      )
    )
  }

  def sbtghactionsSettings: Seq[Setting[_]] = {
    import sbtghactions._
    import sbtghactions.GenerativeKeys._

    val setupHugoStep = WorkflowStep.Run(
      List("""
      |echo "$HOME/bin" > $GITHUB_PATH
      |HUGO_VERSION=0.26 scripts/install-hugo
    """.stripMargin),
      name = Some("Setup Hugo"))

    def siteBuildJob(subproject: String) =
      WorkflowJob(
        id = subproject,
        name = s"Build $subproject",
        scalas = List(scala_212),
        steps = List(
          WorkflowStep.CheckoutFull,
          WorkflowStep.SetupScala,
          setupHugoStep,
          WorkflowStep.Sbt(List(s"$subproject/makeSite"), name = Some(s"Build $subproject"))
        )
      )

    def sitePublishStep(subproject: String) = WorkflowStep.Run(
      List(s"""
      |eval "$$(ssh-agent -s)"
      |echo "$$SSH_PRIVATE_KEY" | ssh-add -
      |git config --global user.name "GitHub Actions CI"
      |git config --global user.email "ghactions@invalid"
      |sbt ++$scala_212 $subproject/makeSite $subproject/ghpagesPushSite
      |
      """.stripMargin),
      name = Some(s"Publish $subproject"),
      env = Map("SSH_PRIVATE_KEY" -> "${{ secrets.SSH_PRIVATE_KEY }}")
    )

    Http4sOrgPlugin.githubActionsSettings ++ Seq(
      githubWorkflowBuild := Seq(
        WorkflowStep
          .Sbt(List("scalafmtCheckAll"), name = Some("Check formatting")),
        WorkflowStep.Sbt(List("headerCheck", "test:headerCheck"), name = Some("Check headers")),
        WorkflowStep.Sbt(List("test:compile"), name = Some("Compile")),
        WorkflowStep.Sbt(List("mimaReportBinaryIssues"), name = Some("Check binary compatibility")),
        WorkflowStep.Sbt(
          List("unusedCompileDependenciesTest"),
          name = Some("Check unused dependencies")),
        WorkflowStep.Sbt(List("test"), name = Some("Run tests")),
        WorkflowStep.Sbt(List("doc"), name = Some("Build docs"))
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
        RefPredicate.StartsWith(Ref.Tag("v"))
      ),
      githubWorkflowPublishPostamble := Seq(
        setupHugoStep,
        sitePublishStep("website")
        // sitePublishStep("docs")
      ),
      // this results in nonexistant directories trying to be compressed
      githubWorkflowArtifactUpload := false,
      githubWorkflowAddedJobs := Seq(
        siteBuildJob("website"),
        siteBuildJob("docs")
      )
    )
  }

  object V { // Dependency versions
    // We pull multiple modules from several projects. This is a convenient
    // reference of all the projects we depend on, and hopefully will reduce
    // error-prone merge conflicts in the dependencies below.
    val asyncHttpClient = "2.12.3"
    val blaze = "0.15.1"
    val boopickle = "1.3.3"
    val caseInsensitive = "1.1.4"
    val cats = "2.6.1"
    val catsEffect = "3.1.1"
    val catsEffectTestingSpecs2 = "1.1.1"
    val catsParse = "0.3.4"
    val circe = "0.15.0-M1"
    val cryptobits = "1.3"
    val disciplineCore = "1.1.5"
    val dropwizardMetrics = "4.2.3"
    val fs2 = "3.0-71-d8ec6dd"
    val ip4s = "3.0.3"
    val jacksonDatabind = "2.12.3"
    val jawn = "1.2.0"
    val jawnFs2 = "2.1.0"
    val jetty = "9.4.41.v20210516"
    val keypool = "0.4.6"
    val literally = "1.0.2"
    val logback = "1.2.3"
    val log4cats = "2.1.1"
    val log4s = "1.10.0"
    val munit = "0.7.27"
    val munitCatsEffect = "1.0.3"
    val munitDiscipline = "1.0.9"
    val netty = "4.1.65.Final"
    val okio = "2.10.0"
    val okhttp = "4.9.1"
    val playJson = "2.9.2"
    val prometheusClient = "0.10.0"
    val reactiveStreams = "1.0.3"
    val quasiquotes = "2.1.0"
    val scalacheck = "1.15.4"
    val scalacheckEffect = "1.0.2"
    val scalaJavaTime = "2.3.0"
    val scalaJsDom = "1.1.0"
    val scalatags = "0.9.4"
    val scalaXml = "2.0.0"
    val scodecBits = "1.1.27"
    val servlet = "3.1.0"
    val slf4j = "1.7.31"
    val specs2 = "4.12.2"
    val tomcat = "9.0.46"
    val treehugger = "0.4.4"
    val twirl = "1.4.2"
    val vault = "3.0.3"
  }

  lazy val asyncHttpClient = "org.asynchttpclient" % "async-http-client" % V.asyncHttpClient
  lazy val blazeCore = Def.setting("org.http4s" %%% "blaze-core" % V.blaze)
  lazy val blazeHttp = Def.setting("org.http4s" %%% "blaze-http" % V.blaze)
  lazy val boopickle = Def.setting("io.suzaku" %%% "boopickle" % V.boopickle)
  lazy val caseInsensitive = Def.setting("org.typelevel" %%% "case-insensitive" % V.caseInsensitive)
  lazy val caseInsensitiveTesting =
    Def.setting("org.typelevel" %%% "case-insensitive-testing" % V.caseInsensitive)
  lazy val catsCore = Def.setting("org.typelevel" %%% "cats-core" % V.cats)
  lazy val catsEffect = Def.setting("org.typelevel" %%% "cats-effect" % V.catsEffect)
  lazy val catsEffectStd = Def.setting("org.typelevel" %%% "cats-effect-std" % V.catsEffect)
  lazy val catsEffectLaws = Def.setting("org.typelevel" %%% "cats-effect-laws" % V.catsEffect)
  lazy val catsEffectTestingSpecs2 =
    Def.setting("org.typelevel" %%% "cats-effect-testing-specs2" % V.catsEffectTestingSpecs2)
  lazy val catsEffectTestkit = Def.setting("org.typelevel" %%% "cats-effect-testkit" % V.catsEffect)
  lazy val catsLaws = Def.setting("org.typelevel" %%% "cats-laws" % V.cats)
  lazy val catsParse = Def.setting("org.typelevel" %%% "cats-parse" % V.catsParse)
  lazy val circeCore = Def.setting("io.circe" %%% "circe-core" % V.circe)
  lazy val circeGeneric = Def.setting("io.circe" %%% "circe-generic" % V.circe)
  lazy val circeJawn = Def.setting("io.circe" %%% "circe-jawn" % V.circe)
  lazy val circeLiteral = Def.setting("io.circe" %%% "circe-literal" % V.circe)
  lazy val circeParser = Def.setting("io.circe" %%% "circe-parser" % V.circe)
  lazy val circeTesting = Def.setting("io.circe" %%% "circe-testing" % V.circe)
  lazy val cryptobits = Def.setting("org.reactormonk" %%% "cryptobits" % V.cryptobits)
  lazy val disciplineCore = Def.setting("org.typelevel" %%% "discipline-core" % V.disciplineCore)
  lazy val dropwizardMetricsCore = "io.dropwizard.metrics" % "metrics-core" % V.dropwizardMetrics
  lazy val dropwizardMetricsJson = "io.dropwizard.metrics" % "metrics-json" % V.dropwizardMetrics
  lazy val fs2Core = Def.setting("co.fs2" %%% "fs2-core" % V.fs2)
  lazy val fs2Io = Def.setting("co.fs2" %%% "fs2-io" % V.fs2)
  lazy val fs2ReactiveStreams = Def.setting("co.fs2" %%% "fs2-reactive-streams" % V.fs2)
  lazy val ip4sCore = Def.setting("com.comcast" %%% "ip4s-core" % V.ip4s)
  lazy val ip4sTestKit = Def.setting("com.comcast" %%% "ip4s-test-kit" % V.ip4s)
  lazy val javaxServletApi = "javax.servlet" % "javax.servlet-api" % V.servlet
  lazy val jawnFs2 = Def.setting("org.typelevel" %%% "jawn-fs2" % V.jawnFs2)
  lazy val jawnParser = Def.setting("org.typelevel" %%% "jawn-parser" % V.jawn)
  lazy val jawnPlay = Def.setting("org.typelevel" %%% "jawn-play" % V.jawn)
  lazy val jettyClient = "org.eclipse.jetty" % "jetty-client" % V.jetty
  lazy val jettyHttp = "org.eclipse.jetty" % "jetty-http" % V.jetty
  lazy val jettyHttp2Server = "org.eclipse.jetty.http2" % "http2-server" % V.jetty
  lazy val jettyRunner = "org.eclipse.jetty" % "jetty-runner" % V.jetty
  lazy val jettyServer = "org.eclipse.jetty" % "jetty-server" % V.jetty
  lazy val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % V.jetty
  lazy val jettyUtil = "org.eclipse.jetty" % "jetty-util" % V.jetty
  lazy val keypool = Def.setting("org.typelevel" %%% "keypool" % V.keypool)
  lazy val literally = Def.setting("org.typelevel" %%% "literally" % V.literally)
  lazy val log4catsCore = Def.setting("org.typelevel" %%% "log4cats-core" % V.log4cats)
  lazy val log4catsNoop = Def.setting("org.typelevel" %%% "log4cats-noop" % V.log4cats)
  lazy val log4catsSlf4j = Def.setting("org.typelevel" %%% "log4cats-slf4j" % V.log4cats)
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
  lazy val playJson = Def.setting("com.typesafe.play" %%% "play-json" % V.playJson)
  lazy val prometheusClient = "io.prometheus" % "simpleclient" % V.prometheusClient
  lazy val prometheusCommon = "io.prometheus" % "simpleclient_common" % V.prometheusClient
  lazy val prometheusHotspot = "io.prometheus" % "simpleclient_hotspot" % V.prometheusClient
  lazy val reactiveStreams = "org.reactivestreams" % "reactive-streams" % V.reactiveStreams
  lazy val quasiquotes = Def.setting("org.scalamacros" %%% "quasiquotes" % V.quasiquotes)
  lazy val scalacheck = Def.setting("org.scalacheck" %%% "scalacheck" % V.scalacheck)
  lazy val scalacheckEffect =
    Def.setting("org.typelevel" %%% "scalacheck-effect" % V.scalacheckEffect)
  lazy val scalacheckEffectMunit =
    Def.setting("org.typelevel" %%% "scalacheck-effect-munit" % V.scalacheckEffect)
  lazy val scalaJavaTime = Def.setting("io.github.cquiroz" %%% "scala-java-time" % V.scalaJavaTime)
  lazy val scalaJsDom = Def.setting("org.scala-js" %%% "scalajs-dom" % V.scalaJsDom)
  def scalaReflect(sv: String) = "org.scala-lang" % "scala-reflect" % sv
  lazy val scalatagsApi = Def.setting("com.lihaoyi" %%% "scalatags" % V.scalatags)
  lazy val scalaXml = Def.setting("org.scala-lang.modules" %%% "scala-xml" % V.scalaXml)
  lazy val scodecBits = Def.setting("org.scodec" %%% "scodec-bits" % V.scodecBits)
  lazy val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j
  lazy val specs2 = Def.setting("org.specs2" %%% "specs2-core" % V.specs2)
  lazy val tomcatCatalina = "org.apache.tomcat" % "tomcat-catalina" % V.tomcat
  lazy val tomcatCoyote = "org.apache.tomcat" % "tomcat-coyote" % V.tomcat
  lazy val tomcatUtilScan = "org.apache.tomcat" % "tomcat-util-scan" % V.tomcat
  lazy val treeHugger = Def.setting("com.eed3si9n" %%% "treehugger" % V.treehugger)
  lazy val twirlApi = Def.setting("com.typesafe.play" %%% "twirl-api" % V.twirl)
  lazy val vault = Def.setting("org.typelevel" %%% "vault" % V.vault)
}
