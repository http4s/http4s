import Http4sBuild._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.pgp.PgpKeys._
import sbtunidoc.Plugin.UnidocKeys._

import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
organization in ThisBuild := "org.http4s"
version      in ThisBuild := scalazCrossBuild("0.15.11-SNAPSHOT", scalazVersion.value)
apiVersion   in ThisBuild := version.map(extractApiVersion).value
// The build supports both scalaz `7.1.x` and `7.2.x`. Simply run
// `set scalazVersion in ThisBuild := "7.2.4"` to change which version of scalaz
// is used to build the project.
scalazVersion in ThisBuild := "7.1.13"

// Root project
name := "root"
description := "A minimal, Scala-idiomatic library for HTTP"
noPublishSettings

lazy val core = libraryProject("core")
  .enablePlugins(BuildInfoPlugin)
  .settings(
  description := "Core http4s library for servers and clients",
    buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, apiVersion),
    buildInfoPackage := organization.value,
    libraryDependencies ++= Seq(
      http4sWebsocket,
      log4s,
      macroCompat,
      parboiled,
      scalaCompiler(scalaVersion.value) % "provided",
      scalaReflect(scalaVersion.value) % "provided",
      scalazCore(scalazVersion.value),
      scalazStream(scalazVersion.value)
    ),
    macroParadiseSetting
)

lazy val testing = libraryProject("testing")
  .settings(
  description := "Instances and laws for testing http4s code",
    libraryDependencies ++= Seq(
      scalacheck
    )
)
  .dependsOn(core)

// Defined outside core/src/test so it can depend on published testing
lazy val tests = libraryProject("tests")
  .settings(
    description := "Tests for core project",
    mimaPreviousArtifacts := Set.empty
  )
  .dependsOn(core, testing % "test->test")

lazy val server = libraryProject("server")
  .settings(
  description := "Base library for building http4s servers"
)
  .dependsOn(core, testing % "test->test", theDsl % "test->compile")

lazy val serverMetrics = libraryProject("server-metrics")
  .settings(
  description := "Support for Dropwizard Metrics on the server",
    libraryDependencies ++= Seq(
      metricsCore,
      metricsJson
    )
)
  .dependsOn(server % "compile;test->test")

lazy val client = libraryProject("client")
  .settings(
  description := "Base library for building http4s clients",
    libraryDependencies += jettyServlet % "test"
)
  .dependsOn(core, testing % "test->test", server % "test->compile", theDsl % "test->compile")

lazy val blazeCore = libraryProject("blaze-core")
  .settings(
  description := "Base library for binding blaze to http4s clients and servers",
    libraryDependencies += blaze
)
  .dependsOn(core, testing % "test->test")

lazy val blazeServer = libraryProject("blaze-server")
  .settings(
  description := "blaze implementation for http4s servers"
)
  .dependsOn(blazeCore % "compile;test->test", server % "compile;test->test")

lazy val blazeClient = libraryProject("blaze-client")
  .settings(
  description := "blaze implementation for http4s clients"
)
  .dependsOn(blazeCore % "compile;test->test", client % "compile;test->test")

lazy val asyncHttpClient = libraryProject("async-http-client")
  .settings(
  description := "async http client implementation for http4s clients",
    libraryDependencies ++= Seq(
      Http4sBuild.asyncHttpClient,
      reactiveStreamsTck % "test"
    )
)
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val servlet = libraryProject("servlet")
  .settings(
  description := "Portable servlet implementation for http4s servers",
    libraryDependencies ++= Seq(
      javaxServletApi % "provided",
      jettyServer % "test",
      jettyServlet % "test"
    )
)
  .dependsOn(server % "compile;test->test")

lazy val jetty = libraryProject("jetty")
  .settings(
  description := "Jetty implementation for http4s servers",
    libraryDependencies ++= Seq(
      jettyServlet
    )
)
  .dependsOn(servlet % "compile;test->test", theDsl % "test->test")

lazy val tomcat = libraryProject("tomcat")
  .settings(
  description := "Tomcat implementation for http4s servers",
    libraryDependencies ++= Seq(
      tomcatCatalina,
      tomcatCoyote
    )
)
  .dependsOn(servlet % "compile;test->test")

// `dsl` name conflicts with modern SBT
lazy val theDsl = libraryProject("dsl")
  .settings(
  description := "Simple DSL for writing http4s services"
)
  .dependsOn(core, testing % "test->test")

lazy val jawn = libraryProject("jawn")
  .settings(
  description := "Base library to parse JSON to various ASTs for http4s",
    libraryDependencies += jawnStreamz(scalazVersion.value)
)
  .dependsOn(core, testing % "test->test")

lazy val argonaut = libraryProject("argonaut")
  .settings(
  description := "Provides Argonaut codecs for http4s",
    libraryDependencies ++= Seq(
      Http4sBuild.argonaut
    )
)
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val circe = libraryProject("circe")
  .settings(
  description := "Provides Circe codecs for http4s",
    libraryDependencies += circeJawn
)
  .dependsOn(core, testing % "test->test", jawn % "compile;test->test")

lazy val json4s = libraryProject("json4s")
  .settings(
  description := "Base library for json4s codecs for http4s",
    libraryDependencies ++= Seq(
      jawnJson4s,
      json4sCore
    )
)
  .dependsOn(jawn % "compile;test->test")

lazy val json4sNative = libraryProject("json4s-native")
  .settings(
  description := "Provides json4s-native codecs for http4s",
    libraryDependencies += Http4sBuild.json4sNative
)
  .dependsOn(json4s % "compile;test->test")

lazy val json4sJackson = libraryProject("json4s-jackson")
  .settings(
  description := "Provides json4s-jackson codecs for http4s",
    libraryDependencies += Http4sBuild.json4sJackson
)
  .dependsOn(json4s % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
  description := "Provides scala-xml codecs for http4s",
    libraryDependencies ++= scalaVersion (VersionNumber(_).numbers match {
      case Seq(2, scalaMajor, _*) if scalaMajor >= 11 => Seq(Http4sBuild.scalaXml)
      case _ => Seq.empty
    }).value
)
  .dependsOn(core, testing % "test->test")

lazy val twirl = http4sProject("twirl")
  .settings(
  description := "Twirl template support for http4s",
    libraryDependencies += twirlApi
)
  .enablePlugins(SbtTwirl)
  .dependsOn(core, testing % "test->test")

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(
  description := "Benchmarks for http4s"
)
  .dependsOn(core)

lazy val loadTest = http4sProject("load-test")
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(
  description := "Load tests for http4s servers",
    libraryDependencies ++= Seq(
      gatlingHighCharts,
      gatlingTest
    ).map(_ % "it,test")
)
  .enablePlugins(GatlingPlugin)

lazy val tutQuick2 = TaskKey[Seq[(File, String)]]("tutQuick2", "Run tut incrementally on recently changed files")


val preStageSiteDirectory = SettingKey[File]("pre-stage-site-directory")
val siteStageDirectory    = SettingKey[File]("site-stage-directory")
val copySiteToStage       = TaskKey[Unit]("copy-site-to-stage")
val exportMetadataForSite = TaskKey[File]("export-metadata-for-site", "Export build metadata, like http4s and key dependency versions, for use in tuts and when building site")
lazy val docs = http4sProject("docs")
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(unidocSettings)
  .settings(ghpages.settings)
  .settings(tutSettings)
  .enablePlugins(HugoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      circeGeneric,
      circeLiteral,
      cryptobits
    ),
    description := "Documentation for http4s",
    autoAPIMappings := true,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject --
      inProjects( // TODO would be nice if these could be introspected from noPublishSettings
        bench,
        examples,
        examplesBlaze,
        examplesJetty,
        examplesTomcat,
        examplesWar,
        loadTest
      ),
    // documentation source code linking
    scalacOptions in (Compile,doc) ++= {
      scmInfo.value match {
        case Some(s) =>
          val isMaster = git.gitCurrentBranch.value == "master"
          val isSnapshot = git.gitCurrentTags.value.map(git.gitTagToVersionNumber.value).flatten.isEmpty

          val path =
            if (isSnapshot && isMaster)
              s"${s.browseUrl}/tree/master€{FILE_PATH}.scala"
            else if (isSnapshot)
              s"${s.browseUrl}/blob/${git.gitHeadCommit.value.get}€{FILE_PATH}.scala"
            else
              s"${s.browseUrl}/blob/v${version.value}€{FILE_PATH}.scala"

          Seq(
            "-implicits",
            "-doc-source-url", path,
            "-sourcepath", (baseDirectory in ThisBuild).value.getAbsolutePath
          )
        case _ => Seq.empty
      }
    },
    preStageSiteDirectory := sourceDirectory.value / "hugo",
    siteStageDirectory := target.value / "site-stage",
    sourceDirectory in Hugo := siteStageDirectory.value,
    watchSources := {
      // nasty hack to remove the target directory from watched sources
      watchSources.value
        .filterNot(_.getAbsolutePath.startsWith(
          target.value.getAbsolutePath))
    },
    copySiteToStage := {
      streams.value.log.debug(s"copying ${preStageSiteDirectory.value} to ${siteStageDirectory.value}")

      IO.copyDirectory(
        source = preStageSiteDirectory.value,
        target = siteStageDirectory.value,
        overwrite = false,
        preserveLastModified = true)
      IO.copyDirectory(
        source = tutTargetDirectory.value,
        target = siteStageDirectory.value / "content" / "v0.15",
        overwrite = false,
        preserveLastModified = true)
      IO.copyFile(
        sourceFile = baseDirectory.value / ".." / "CHANGELOG.md",
        targetFile = siteStageDirectory.value / "CHANGELOG.md",
        preserveLastModified = true)
    },
    exportMetadataForSite := {
      val dest = (sourceDirectory in Hugo).value / "data" / "build.toml"
      val (major, minor) = apiVersion.value
      // Would be more elegant if `[versions.http4s]` was nested, but then
      // the index lookups in `shortcodes/version.html` get complicated.
      val buildData: String =
        s"""
           |[versions]
           |"http4s.api" = "$major.$minor"
           |"http4s.current" = "${version.value}"
           |"http4s.doc" = "${docExampleVersion(version.value)}"
           |scalaz = "${scalazVersion.value}"
           |circe = "${circeJawn.revision}"
           |cryptobits = "${cryptobits.revision}"
           |"argonaut-shapeless_6.2" = "1.2.0-M5"
         """.stripMargin
      IO.write(dest, buildData)
      dest
    },
    copySiteToStage := copySiteToStage.dependsOn(tutQuick).value,
    makeSite := makeSite.dependsOn(copySiteToStage, exportMetadataForSite).value,
    baseURL in Hugo := {
      if (isTravisBuild.value) new URI(s"http://http4s.org")
      else new URI(s"http://127.0.0.1:${previewFixedPort.value.getOrElse(4000)}")
    },
    // all .md|markdown files go into `content` dir for hugo processing
    ghpagesNoJekyll := true,
    includeFilter in Hugo := (
        "*.html" |
        "*.png" | "*.jpg" | "*.gif" | "*.ico" | "*.svg" |
        "*.js" | "*.swf" | "*.json" | "*.md" |
        "*.css" | "*.woff" | "*.woff2" | "*.ttf" |
        "CNAME" | "_config.yml"
    ),
    siteMappings := {
      if (Http4sGhPages.buildMainSite) siteMappings.value
      else {
        val (major, minor) = apiVersion.value
        val prefix = s"/v${major}.${minor}/"
        siteMappings.value.filter {
          case (_, d) if d.startsWith(prefix) => true
          case _ => false
        }
      }
    },
    siteMappings ++= {
      val m = (mappings in (ScalaUnidoc, packageDoc)).value
      val (major, minor) = apiVersion.value
      for ((f, d) <- m) yield (f, s"v$major.$minor/api/$d")
    },
    cleanSite := Http4sGhPages.cleanSiteForRealz(updatedRepository.value, gitRunner.value, streams.value, apiVersion.value),
    synchLocal := Http4sGhPages.synchLocalForRealz(privateMappings.value, updatedRepository.value, ghpagesNoJekyll.value, gitRunner.value, streams.value, apiVersion.value),
    git.remoteRepo := "git@github.com:http4s/http4s.git"
  )
  .dependsOn(client, core, theDsl, blazeServer, blazeClient, circe)


lazy val examples = http4sProject("examples")
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .settings(
  description := "Common code for http4s examples",
    libraryDependencies ++= Seq(
      circeGeneric,
      logbackClassic % "runtime",
      jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
    )
)
  .dependsOn(server, serverMetrics, theDsl, circe, scalaXml, twirl)
  .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
  description := "Examples of http4s server and clients on blaze",
    fork := true,
    libraryDependencies ++= Seq(alpnBoot, metricsJson),
    macroParadiseSetting,
    javaOptions in run ++= ((managedClasspath in Runtime) map { attList =>
      for {
        file <- attList.map(_.data)
        path = file.getAbsolutePath if path.contains("jetty.alpn")
      } yield { s"-Xbootclasspath/p:${path}" }
    }).value
)
  .dependsOn(blazeServer, blazeClient)

lazy val examplesJetty = exampleProject("examples-jetty")
  .settings(Revolver.settings)
  .settings(
  description := "Example of http4s server on Jetty",
    fork := true,
    mainClass in reStart := Some("com.example.http4s.jetty.JettyExample")
)
  .dependsOn(jetty)

lazy val examplesTomcat = exampleProject("examples-tomcat")
  .settings(Revolver.settings)
  .settings(
  description := "Example of http4s server on Tomcat",
    fork := true,
    mainClass in reStart := Some("com.example.http4s.tomcat.TomcatExample")
)
  .dependsOn(tomcat)

// Run this with jetty:start
lazy val examplesWar = exampleProject("examples-war")
  .enablePlugins(JettyPlugin)
  .settings(
  description := "Example of a WAR deployment of an http4s service",
    fork := true,
    libraryDependencies ++= Seq(
      javaxServletApi % "provided",
      logbackClassic % "runtime"
    )
)
  .dependsOn(servlet)

def http4sProject(name: String) = Project(name, file(name))
  .settings(commonSettings)
  .settings(projectMetadata)
  .settings(publishSettings)
  .settings(
    moduleName := s"http4s-$name",
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2,"xonly", "failtrace"),
    initCommands()
  )

def libraryProject(name: String) = http4sProject(name)
  .settings(mimaSettings)

def exampleProject(name: String) = http4sProject(name)
  .in(file(name.replace("examples-", "examples/")))
  .settings(noPublishSettings)
  .settings(noCoverageSettings)
  .dependsOn(examples)

lazy val apiVersion = taskKey[(Int, Int)]("Defines the API compatibility version for the project.")
lazy val jvmTarget = taskKey[String]("Defines the target JVM version for object files.")
lazy val scalazVersion = settingKey[String]("The version of Scalaz used for building.")

lazy val projectMetadata = Seq(
  homepage := Some(url("http://http4s.org/")),
  startYear := Some(2013),
  licenses := Seq(
    "Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  scmInfo := {
    val base = "github.com/http4s/http4s"
    Some(ScmInfo(url(s"https://$base"), s"scm:git:https://$base", Some(s"scm:git:git@$base")))
  },
  pomExtra := (
    <developers>
      <developer>
      <id>rossabaker</id>
      <name>Ross A. Baker</name>
      <email>ross@rossabaker.com</email>
      </developer>
      <developer>
      <id>casualjim</id>
      <name>Ivan Porto Carrero</name>
      <email>ivan@flanders.co.nz</email>
      <url>http://flanders.co.nz</url>
        </developer>
      <developer>
      <id>brycelane</id>
      <name>Bryce L. Anderson</name>
      <email>bryce.anderson22@gmail.com</email>
      </developer>
      <developer>
      <id>before</id>
      <name>André Rouél</name>
      </developer>
      <developer>
      <id>julien-truffaut</id>
      <name>Julien Truffaut</name>
      </developer>
      <developer>
      <id>kryptt</id>
      <name>Rodolfo Hansen</name>
      </developer>
      </developers>
  )
)

lazy val commonSettings = Seq(
  jvmTarget := scalaVersion.map {
    VersionNumber(_).numbers match {
      case Seq(2, 10, _*) => "1.7"
      case _ => "1.8"
    }
  }.value,
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    s"-target:jvm-${jvmTarget.value}",
    "-unchecked",
    "-Xfatal-warnings",
    "-Yinline-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"
  ),
  scalacOptions in (Compile, doc) -= "-Xfatal-warnings", // broken references to other modules
  scalacOptions := {
    // We're deprecation-clean across Scala versions, but not across scalaz
    // versions.  This is not worth maintaining a branch.
    VersionNumber(scalazVersion.value).numbers match {
      case Seq(7, 1, _) =>
        scalacOptions.value
      case _ =>
        // This filtering does not trigger when scalazVersion is changed in a
        // running SBT session.  Help wanted.
        scalacOptions.value filterNot (_ == "-Xfatal-warnings")
    }
  },
  scalacOptions ++= scalaVersion.map { v =>
    if (delambdafyOpts(v)) Seq(
      "-Ybackend:GenBCode"
    ) else Seq.empty
  }.value,
  scalacOptions -= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => "-Yinline-warnings"
      case _ => ""
    }
  },
  javacOptions ++= Seq(
    "-source", jvmTarget.value,
    "-target", jvmTarget.value,
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
  libraryDependencies ++= scalaVersion(v =>
    if (delambdafyOpts(v)) Seq("org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0")
    else Seq.empty
  ).value,
  libraryDependencies ++= scalazVersion(sz => Seq(
    discipline,
    logbackClassic,
    scalazScalacheckBinding(sz),
    specs2Core(sz),
    specs2MatcherExtra(sz),
    specs2Scalacheck(sz)
  ).map(_ % "test")).value,
  // don't include scoverage as a dependency in the pom
  // https://github.com/scoverage/sbt-scoverage/issues/153
  // this code was copied from https://github.com/mongodb/mongo-spark
  pomPostProcess := { (node: xml.Node) =>
    new RuleTransformer(
      new RewriteRule {
        override def transform(node: xml.Node): Seq[xml.Node] = node match {
          case e: xml.Elem
              if e.label == "dependency" && e.child.exists(child => child.label == "groupId" && child.text == "org.scoverage") => Nil
          case _ => Seq(node)

        }

      }).transform(node).head
  },
  coursierVerbosity := 0,
  ivyLoggingLevel := UpdateLogging.Quiet // This doesn't seem to work? We see this in MiMa
) ++ xlint

lazy val publishSettings = Seq(
  credentials ++= sonatypeEnvCredentials
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishSigned := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val noCoverageSettings = Seq(
  coverageExcludedPackages := ".*"
)

lazy val mimaSettings = Seq(
  mimaFailOnProblem := version.zipWith(scalazVersion)(compatibleVersion(_, _).isDefined).value,
  mimaPreviousArtifacts := (compatibleVersion(version.value, scalazVersion.value) map {
    organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % _
  }).toSet,
  mimaBinaryIssueFilters ++= {
    import com.typesafe.tools.mima.core._
    import com.typesafe.tools.mima.core.ProblemFilters._
    Seq(
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.arbitraryIPv4"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.arbitraryIPv6"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.genSubDelims"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.arbitraryUriHost"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.arbitraryAuthority"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.genCharsetRangeNoQuality"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.genHexDigit"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.genPctEncoded"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.arbitraryUri"),
      exclude[ReversedMissingMethodProblem]("org.http4s.testing.ArbitraryInstances.genUnreserved"),
      exclude[ReversedMissingMethodProblem]("org.http4s.RequestOps.addCookie"),
      exclude[ReversedMissingMethodProblem]("org.http4s.RequestOps.addCookie"),
      exclude[ReversedMissingMethodProblem]("org.http4s.RequestOps.addCookie$default$3"),
      exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.BlazeConnection.runRequest"),
      exclude[DirectAbstractMethodProblem]("org.http4s.client.blaze.BlazeConnection.runRequest"),
      exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Connection.runRequest")
    )
  }
)

// Check whether to enable java 8 type lambdas
// https://github.com/scala/make-release-notes/blob/2.11.x/experimental-backend.md
// Minimum scala version is 2.11.8 due to sbt/sbt#2076
def delambdafyOpts(v: String): Boolean = VersionNumber(v).numbers match {
  case Seq(2, 11, x, _*) if x > 7 => true
  case _ => false
}

def initCommands(additionalImports: String*) =
  initialCommands := (List(
    "scalaz._",
    "Scalaz._",
    "scalaz.concurrent.Task",
    "org.http4s._"
  ) ++ additionalImports).mkString("import ", ", ", "")

lazy val xlint = Seq(
  scalacOptions += {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => "-Xlint:-unused,_"
      case _ => "-Xlint"
    }
  }
)
