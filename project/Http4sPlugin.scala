package org.http4s.sbt

import com.timushev.sbt.updates.UpdatesPlugin.autoImport._ // autoImport vs. UpdateKeys necessary here for implicit
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.git.JGit
import de.heikoseeberger.sbtheader.{License, LicenseStyle}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport.unusedCompileDependenciesFilter
import sbt.Keys._
import sbt._

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running on CI")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
    val http4sBuildData = taskKey[Unit]("Export build metadata for Hugo")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = Http4sOrgPlugin

  val scala_213 = "2.13.3"
  val scala_212 = "2.12.12"

  override lazy val buildSettings = Seq(
    // Many steps only run on one build. We distinguish the primary build from
    // secondary builds by the Travis build number.
    isCi := sys.env.get("CI").isDefined,
    ThisBuild / http4sApiVersion := (ThisBuild / version).map {
      case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
    }.value,
  )

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    scalaVersion := scala_213,
    crossScalaVersions := Seq(scala_213, scala_212, "0.27.0-RC1"),

    http4sBuildData := {
      val dest = target.value / "hugo-data" / "build.toml"
      val (major, minor) = http4sApiVersion.value

      val releases = latestPerMinorVersion(baseDirectory.value)
        .map { case ((major, minor), v) => s""""$major.$minor" = "${v.toString}""""}
        .mkString("\n")

      // Would be more elegant if `[versions.http4s]` was nested, but then
      // the index lookups in `shortcodes/version.html` get complicated.
      val buildData: String =
        s"""
           |[versions]
           |"http4s.api" = "$major.$minor"
           |"http4s.current" = "${version.value}"
           |"http4s.doc" = "${docExampleVersion(version.value)}"
           |circe = "${circeJawn.revision}"
           |cryptobits = "${cryptobits.revision}"
           |"argonaut-shapeless_6.2" = "1.2.0-M6"
           |
           |[releases]
           |$releases
         """.stripMargin

      IO.write(dest, buildData)
    },

    // servlet-4.0 is not yet supported by jetty-9 or tomcat-9, so don't accidentally depend on its new features
    dependencyUpdatesFilter -= moduleFilter(organization = "javax.servlet", revision = "4.0.0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "javax.servlet", revision = "4.0.1"),
    // breaks binary compatibility in caffeine module with 0.8.1
    dependencyUpdatesFilter -= moduleFilter(organization = "io.prometheus", revision = "0.9.0"),
    // Jetty prereleases appear because of their non-semver prod releases
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "10.0.0-alpha0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "10.0.0.alpha1"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "10.0.0.alpha2"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "10.0.0.beta0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "10.0.0.beta1"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "10.0.0.beta2"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "11.0.0-alpha0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "11.0.0.beta1"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty", revision = "11.0.0.beta2"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "10.0.0-alpha0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "10.0.0.alpha1"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "10.0.0.alpha2"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "10.0.0.beta0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "10.0.0.beta1"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "10.0.0.beta2"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "11.0.0-alpha0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "11.0.0.beta1"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.eclipse.jetty.http2", revision = "11.0.0.beta2"),
    // Broke binary compatibility with 2.10.5
    dependencyUpdatesFilter -= moduleFilter(organization = "org.asynchttpclient", revision = "2.11.0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.asynchttpclient", revision = "2.12.0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.asynchttpclient", revision = "2.12.1"),
    // No release notes. If it's compatible with 6.2.5, prove it and PR it.
    dependencyUpdatesFilter -= moduleFilter(organization = "io.argonaut", revision = "6.3.0"),
    dependencyUpdatesFilter -= moduleFilter(organization = "io.argonaut", revision = "6.3.1"),
    // Cursed release. Calls ByteBuffer incompatibly with JDK8
    dependencyUpdatesFilter -= moduleFilter(name = "boopickle", revision = "1.3.2"),
    // Dropped joda-time support, wait for next breaking release
    dependencyUpdatesFilter -= moduleFilter(organization = "com.typesafe.play", revision = "2.9.0"),

    excludeFilter.in(headerSources) := HiddenFileFilter ||
      new FileFilter {
        def accept(file: File) = {
          attributedSources.contains(baseDirectory.value.toPath.relativize(file.toPath).toString)
        }

        val attributedSources = Set(
          "src/main/scala/org/http4s/argonaut/Parser.scala",
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
          "src/main/scala/org/http4s/util/UrlCoding.scala",
          "src/main/scala/org/http4s/dsl/impl/Path.scala",
          "src/test/scala/org/http4s/dsl/PathSpec.scala",
          "src/main/scala/org/http4s/ember/core/ChunkedEncoding.scala",
          "src/main/scala/org/http4s/testing/ErrorReportingUtils.scala",
          "src/main/scala/org/http4s/testing/IOMatchers.scala",
          "src/main/scala/org/http4s/testing/RunTimedMatchers.scala",
          "src/test/scala/org/http4s/Http4sSpec.scala",
          "src/test/scala/org/http4s/util/illTyped.scala",
          "src/test/scala/org/http4s/testing/ErrorReporting.scala",
          "src/test/scala/org/http4s/UriSpec.scala"
        )
      },
  )

  def extractApiVersion(version: String) = {
    val VersionExtractor = """(\d+)\.(\d+)\..*""".r
    version match {
      case VersionExtractor(major, minor) => (major.toInt, minor.toInt)
    }
  }

  def extractDocsPrefix(version: String) =
    extractApiVersion(version).productIterator.mkString("/v", ".", "")

  /**
    * @return the version we want to document, for example in tuts,
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
      case VersionNumber(Seq(_, _, patch), Seq(q), _) if q startsWith "M" =>
        (patch, 0L, q.drop(1).toLong)
      case VersionNumber(Seq(_, _, patch), Seq(q), _) if q startsWith "RC" =>
        (patch, 1L, q.drop(2).toLong)
      case VersionNumber(Seq(_, _, patch), Seq(), _) => (patch, 2L, 0L)
      case _ => (-1L, -1L, -1L)
    }

    JGit(file).tags.collect {
      case ref if ref.getName.startsWith("refs/tags/v") =>
        VersionNumber(ref.getName.substring("refs/tags/v".size))
    }.foldLeft(Map.empty[(Long, Long), VersionNumber]) {
      case (m, v) =>
        majorMinor(v) match {
          case Some(key) =>
            val max = m.get(key).fold(v) { v0 => Ordering[(Long, Long, Long)].on(patchSortKey).max(v, v0) }
            m.updated(key, max)
          case None => m
        }
    }
  }

  object V { // Dependency versions
    // We pull multiple modules from several projects. This is a convenient
    // reference of all the projects we depend on, and hopefully will reduce
    // error-prone merge conflicts in the dependencies below.
    val argonaut = "6.2.5"
    val asyncHttpClient = "2.10.5"
    val blaze = "0.14.13"
    val boopickle = "1.3.3"
    val cats = "2.2.0"
    val catsEffect = "2.2.0"
    val catsEffectTesting = "0.4.1"
    val circe = "0.13.0"
    val cryptobits = "1.3"
    val disciplineSpecs2 = "1.1.0"
    val dropwizardMetrics = "4.1.13"
    val fs2 = "2.4.4"
    val jawn = "1.0.0"
    val jawnFs2 = "1.0.0"
    val jetty = "9.4.32.v20200930"
    val json4s = "3.6.10"
    val log4cats = "1.1.1"
    val keypool = "0.2.0"
    val logback = "1.2.3"
    val log4s = "1.8.2"
    val mockito = "3.5.13"
    val netty = "4.1.53.Final"
    val okhttp = "4.9.0"
    val parboiledHttp4s = "2.0.1"
    val playJson = "2.9.1"
    val prometheusClient = "0.8.1"
    val quasiquotes = "2.1.0"
    val scalacheck = "1.14.3"
    val scalafix = _root_.scalafix.sbt.BuildInfo.scalafixVersion
    val scalatags = "0.9.2"
    val scalaXml = "1.3.0"
    val servlet = "3.1.0"
    val specs2 = "4.10.5"
    val tomcat = "9.0.39"
    val treehugger = "0.4.4"
    val twirl = "1.4.2"
    val vault = "2.0.0"
  }

  lazy val argonaut                         = "io.argonaut"            %% "argonaut"                  % V.argonaut
  lazy val asyncHttpClient                  = "org.asynchttpclient"    %  "async-http-client"         % V.asyncHttpClient
  lazy val blaze                            = "org.http4s"             %% "blaze-http"                % V.blaze
  lazy val boopickle                        = "io.suzaku"              %% "boopickle"                 % V.boopickle
  lazy val cats                             = "org.typelevel"          %% "cats-core"                 % V.cats
  lazy val catsEffect                       = "org.typelevel"          %% "cats-effect"               % V.catsEffect
  lazy val catsEffectLaws                   = "org.typelevel"          %% "cats-effect-laws"          % V.catsEffect
  lazy val catsEffectTestingSpecs2          = "com.codecommit"         %% "cats-effect-testing-specs2" % V.catsEffectTesting
  lazy val catsKernelLaws                   = "org.typelevel"          %% "cats-kernel-laws"          % V.cats
  lazy val catsLaws                         = "org.typelevel"          %% "cats-laws"                 % V.cats
  lazy val circeGeneric                     = "io.circe"               %% "circe-generic"             % V.circe
  lazy val circeJawn                        = "io.circe"               %% "circe-jawn"                % V.circe
  lazy val circeLiteral                     = "io.circe"               %% "circe-literal"             % V.circe
  lazy val circeParser                      = "io.circe"               %% "circe-parser"              % V.circe
  lazy val circeTesting                     = "io.circe"               %% "circe-testing"             % V.circe
  lazy val cryptobits                       = "org.reactormonk"        %% "cryptobits"                % V.cryptobits
  lazy val disciplineSpecs2                 = "org.typelevel"          %% "discipline-specs2"         % V.disciplineSpecs2
  lazy val dropwizardMetricsCore            = "io.dropwizard.metrics"  %  "metrics-core"              % V.dropwizardMetrics
  lazy val dropwizardMetricsJson            = "io.dropwizard.metrics"  %  "metrics-json"              % V.dropwizardMetrics
  lazy val fs2Io                            = "co.fs2"                 %% "fs2-io"                    % V.fs2
  lazy val fs2ReactiveStreams               = "co.fs2"                 %% "fs2-reactive-streams"      % V.fs2
  lazy val javaxServletApi                  = "javax.servlet"          %  "javax.servlet-api"         % V.servlet
  lazy val jawnFs2                          = "org.http4s"             %% "jawn-fs2"                  % V.jawnFs2
  lazy val jawnJson4s                       = "org.typelevel"          %% "jawn-json4s"               % V.jawn
  lazy val jawnPlay                         = "org.typelevel"          %% "jawn-play"                 % V.jawn
  lazy val jettyClient                      = "org.eclipse.jetty"      %  "jetty-client"              % V.jetty
  lazy val jettyHttp2Server                 = "org.eclipse.jetty.http2" %  "http2-server"             % V.jetty
  lazy val jettyRunner                      = "org.eclipse.jetty"      %  "jetty-runner"              % V.jetty
  lazy val jettyServer                      = "org.eclipse.jetty"      %  "jetty-server"              % V.jetty
  lazy val jettyServlet                     = "org.eclipse.jetty"      %  "jetty-servlet"             % V.jetty
  lazy val json4sCore                       = "org.json4s"             %% "json4s-core"               % V.json4s
  lazy val json4sJackson                    = "org.json4s"             %% "json4s-jackson"            % V.json4s
  lazy val json4sNative                     = "org.json4s"             %% "json4s-native"             % V.json4s
  lazy val keypool                          = "io.chrisdavenport"      %% "keypool"                   % V.keypool
  lazy val log4catsCore                     = "io.chrisdavenport"      %% "log4cats-core"             % V.log4cats
  lazy val log4catsSlf4j                    = "io.chrisdavenport"      %% "log4cats-slf4j"            % V.log4cats
  lazy val log4catsTesting                  = "io.chrisdavenport"      %% "log4cats-testing"          % V.log4cats
  lazy val log4s                            = "org.log4s"              %% "log4s"                     % V.log4s
  lazy val logbackClassic                   = "ch.qos.logback"         %  "logback-classic"           % V.logback
  lazy val mockito                          = "org.mockito"            %  "mockito-core"              % V.mockito
  lazy val nettyCodec                       = "io.netty"               %  "netty-codec"               % V.netty
  lazy val nettyCodecSocks                  = "io.netty"               %  "netty-codec-socks"         % V.netty
  lazy val nettyHandlerProxy                = "io.netty"               %  "netty-handler-proxy"       % V.netty
  lazy val nettyCommon                      = "io.netty"               %  "netty-common"              % V.netty
  lazy val nettyTransport                   = "io.netty"               %  "netty-transport"           % V.netty
  lazy val nettyHandler                     = "io.netty"               %  "netty-handler"             % V.netty
  lazy val nettyResolverDns                 = "io.netty"               %  "netty-resolver-dns"        % V.netty
  lazy val okhttp                           = "com.squareup.okhttp3"   %  "okhttp"                    % V.okhttp
  lazy val playJson                         = "com.typesafe.play"      %% "play-json"                 % V.playJson
  lazy val prometheusClient                 = "io.prometheus"          %  "simpleclient"              % V.prometheusClient
  lazy val prometheusCommon                 = "io.prometheus"          %  "simpleclient_common"       % V.prometheusClient
  lazy val prometheusHotspot                = "io.prometheus"          %  "simpleclient_hotspot"      % V.prometheusClient
  lazy val parboiled                        = "org.http4s"             %% "parboiled"                 % V.parboiledHttp4s
  lazy val quasiquotes                      = "org.scalamacros"        %% "quasiquotes"               % V.quasiquotes
  lazy val scalacheck                       = "org.scalacheck"         %% "scalacheck"                % V.scalacheck
  def scalaReflect(sv: String)              = "org.scala-lang"         %  "scala-reflect"             % sv
  lazy val scalatagsApi                     = "com.lihaoyi"            %% "scalatags"                 % V.scalatags
  lazy val scalaXml                         = "org.scala-lang.modules" %% "scala-xml"                 % V.scalaXml
  lazy val specs2Cats                       = "org.specs2"             %% "specs2-cats"               % V.specs2
  lazy val specs2Core                       = "org.specs2"             %% "specs2-core"               % V.specs2
  lazy val specs2Matcher                    = "org.specs2"             %% "specs2-matcher"            % V.specs2
  lazy val specs2MatcherExtra               = "org.specs2"             %% "specs2-matcher-extra"      % V.specs2
  lazy val specs2Scalacheck                 = "org.specs2"             %% "specs2-scalacheck"         % V.specs2
  lazy val tomcatCatalina                   = "org.apache.tomcat"      %  "tomcat-catalina"           % V.tomcat
  lazy val tomcatCoyote                     = "org.apache.tomcat"      %  "tomcat-coyote"             % V.tomcat
  lazy val treeHugger                       = "com.eed3si9n"           %% "treehugger"                % V.treehugger
  lazy val twirlApi                         = "com.typesafe.play"      %% "twirl-api"                 % V.twirl
  lazy val vault                            = "io.chrisdavenport"      %% "vault"                     % V.vault
}
