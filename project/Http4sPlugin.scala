package org.http4s.sbt

import com.github.tkawachi.doctest.DoctestPlugin.autoImport._
import com.github.sbt.git.SbtGit.git
import com.github.sbt.git.JGit
import com.typesafe.tools.mima.plugin.MimaKeys._
import de.heikoseeberger.sbtheader.{License, LicenseStyle}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport.unusedCompileDependenciesFilter
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import org.typelevel.sbt.TypelevelKernelPlugin.autoImport._
import org.typelevel.sbt.gha.GenerativeKeys._
import org.typelevel.sbt.gha.GitHubActionsKeys._
import org.typelevel.sbt.gha.JavaSpec

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running on CI")
    val http4sApiVersion = settingKey[(Int, Int)]("API version of http4s")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = Http4sOrgPlugin

  val scala_213 = "2.13.16"
  val scala_212 = "2.12.20"
  val scala_3 = "3.3.5"

  override lazy val globalSettings = Seq(
    isCi := githubIsWorkflowBuild.value
  )

  override lazy val buildSettings = Seq(
    // Many steps only run on one build. We distinguish the primary build from
    // secondary builds by the Travis build number.
    http4sApiVersion := {
      version.value match {
        case VersionNumber(Seq(major, minor, _*), _, _) =>
          (major.toInt, minor.toInt)
      }
    }
  )

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    headerSources / excludeFilter := HiddenFileFilter,
    doctestTestFramework := DoctestTestFramework.Munit,
    semanticdbOptions ++= Seq("-P:semanticdb:synthetics:on").filter(_ => !tlIsScala3.value),
  )

  def extractApiVersion(version: String) = {
    val VersionExtractor = """(\d+)\.(\d+)[-.].*""".r
    version match {
      case VersionExtractor(major, minor) => (major.toInt, minor.toInt)
    }
  }

  def extractDocsPrefix(version: String) =
    extractApiVersion(version).productIterator.mkString("/v", ".", "")

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

  object V { // Dependency versions
    // We pull multiple modules from several projects. This is a convenient
    // reference of all the projects we depend on, and hopefully will reduce
    // error-prone merge conflicts in the dependencies below.
    val blaze = "0.15.3"
    val caseInsensitive = "1.4.2"
    val cats = "2.11.0"
    val catsEffect = "3.5.7"
    val catsParse = "1.0.0"
    val circe = "0.14.11"
    val crypto = "0.2.4"
    val cryptobits = "1.3"
    val disciplineCore = "1.6.0"
    val epollcat = "0.1.6"
    val fs2 = "3.11.0"
    val ip4s = "3.6.0"
    val hpack = "1.0.4"
    val javaWebSocket = "1.6.0"
    val jawn = "1.5.1"
    val jawnFs2 = "2.4.0"
    val jnrUnixSocket = "0.38.23"
    val keypool = "0.4.10"
    val literally = "1.1.0"
    val logback = "1.2.6"
    val log4cats = "2.7.0"
    val log4s = "1.10.0"
    val munit = "1.0.0"
    val munitCatsEffect = "2.0.0"
    val munitDiscipline = "2.0.0-M3"
    val netty = "4.1.119.Final"
    val quasiquotes = "2.1.0"
    val scalacheck = "1.17.1"
    val scalacheckEffect = "2.0.0-M2"
    val scalaJavaLocales = "1.5.1"
    val scalaJavaTime = "2.5.0"
    val scodecBits = "1.1.38"
    val slf4j = "1.7.36"
    val treehugger = "0.4.4"
    val twitterHpack = "1.0.2"
    val vault = "3.6.0"
  }

  lazy val blazeCore = "org.http4s" %% "blaze-core" % V.blaze
  lazy val blazeHttp = "org.http4s" %% "blaze-http" % V.blaze
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
  lazy val epollcat = Def.setting("com.armanbilge" %%% "epollcat" % V.epollcat)
  lazy val fs2Core = Def.setting("co.fs2" %%% "fs2-core" % V.fs2)
  lazy val fs2Io = Def.setting("co.fs2" %%% "fs2-io" % V.fs2)
  lazy val ip4sCore = Def.setting("com.comcast" %%% "ip4s-core" % V.ip4s)
  lazy val ip4sTestKit = Def.setting("com.comcast" %%% "ip4s-test-kit" % V.ip4s)
  lazy val hpack = Def.setting("org.http4s" %%% "hpack" % V.hpack)
  lazy val jawnFs2 = Def.setting("org.typelevel" %%% "jawn-fs2" % V.jawnFs2)
  lazy val javaWebSocket = "org.java-websocket" % "Java-WebSocket" % V.javaWebSocket
  lazy val jawnParser = Def.setting("org.typelevel" %%% "jawn-parser" % V.jawn)
  lazy val jnrUnixSocket = "com.github.jnr" % "jnr-unixsocket" % V.jnrUnixSocket
  lazy val keypool = Def.setting("org.typelevel" %%% "keypool" % V.keypool)
  lazy val literally = Def.setting("org.typelevel" %%% "literally" % V.literally)
  lazy val log4catsCore = Def.setting("org.typelevel" %%% "log4cats-core" % V.log4cats)
  lazy val log4catsNoop = Def.setting("org.typelevel" %%% "log4cats-noop" % V.log4cats)
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % V.log4cats
  lazy val log4catsJSConsole = Def.setting("org.typelevel" %%% "log4cats-js-console" % V.log4cats)
  lazy val log4catsTesting = Def.setting("org.typelevel" %%% "log4cats-testing" % V.log4cats)
  lazy val log4s = Def.setting("org.log4s" %%% "log4s" % V.log4s)
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % V.logback
  lazy val munit = Def.setting("org.scalameta" %%% "munit" % V.munit)
  lazy val munitCatsEffect =
    Def.setting("org.typelevel" %%% "munit-cats-effect" % V.munitCatsEffect)
  lazy val munitDiscipline = Def.setting("org.typelevel" %%% "discipline-munit" % V.munitDiscipline)
  lazy val nettyBuffer = "io.netty" % "netty-buffer" % V.netty
  lazy val nettyCodecHttp = "io.netty" % "netty-codec-http" % V.netty
  lazy val quasiquotes = "org.scalamacros" %% "quasiquotes" % V.quasiquotes
  lazy val scalacheck = Def.setting("org.scalacheck" %%% "scalacheck" % V.scalacheck)
  lazy val scalacheckEffect =
    Def.setting("org.typelevel" %%% "scalacheck-effect" % V.scalacheckEffect)
  lazy val scalacheckEffectMunit =
    Def.setting("org.typelevel" %%% "scalacheck-effect-munit" % V.scalacheckEffect)
  lazy val scalaJavaLocalesEnUS =
    Def.setting("io.github.cquiroz" %%% "locales-minimal-en_us-db" % V.scalaJavaLocales)
  lazy val scalaJavaTime = Def.setting("io.github.cquiroz" %%% "scala-java-time" % V.scalaJavaTime)
  def scalaReflect(sv: String) = "org.scala-lang" % "scala-reflect" % sv
  lazy val scodecBits = Def.setting("org.scodec" %%% "scodec-bits" % V.scodecBits)
  lazy val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j
  lazy val treeHugger = "com.eed3si9n" %% "treehugger" % V.treehugger
  lazy val twitterHpack = "com.twitter" % "hpack" % V.twitterHpack
  lazy val vault = Def.setting("org.typelevel" %%% "vault" % V.vault)
}
