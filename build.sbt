import Http4sPlugin._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.pgp.PgpKeys._
import sbtunidoc.Plugin.UnidocKeys._

import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
organization in ThisBuild := "org.http4s"

apiVersion in ThisBuild := (version in ThisBuild).map {
  case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
}.value

// Root project
name := "root"
description := "A minimal, Scala-idiomatic library for HTTP"
enablePlugins(DisablePublishingPlugin)

// This defines macros that we use in core, so it needs to be split out
lazy val parboiled2 = libraryProject("parboiled2")
  .enablePlugins(DisablePublishingPlugin)
  .settings(
    libraryDependencies ++= Seq(
      scalaReflect(scalaOrganization.value, scalaVersion.value) % "provided"
    ),
    unmanagedSourceDirectories in Compile ++= {
      scalaBinaryVersion.value match {
        // The 2.12 branch is compatible with 2.11
        case "2.12" => Seq((sourceDirectory in Compile).value / "scala-2.11")
        case _ => Seq.empty
      }
    },
    // https://issues.scala-lang.org/browse/SI-9490
    (scalacOptions in Compile) --= Seq("-Ywarn-inaccessible", "-Xlint", "-Xlint:inaccessible"),
    macroParadiseSetting
  )

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
      scalaCompiler(scalaOrganization.value, scalaVersion.value) % "provided",
      scalazCore(scalazVersion.value),
      scalazStream(scalazVersion.value)
    ),
    macroParadiseSetting,
    mappings in (Compile, packageBin) ++= (mappings in (parboiled2.project, Compile, packageBin)).value,
    mappings in (Compile, packageSrc) ++= (mappings in (parboiled2.project, Compile, packageSrc)).value,
    mappings in (Compile, packageDoc) ++= (mappings in (parboiled2.project, Compile, packageDoc)).value,
    mappings in (Compile, packageBin) ~= (_.groupBy(_._2).toSeq.map(_._2.head)), // filter duplicate outputs
    mappings in (Compile, packageDoc) ~= (_.groupBy(_._2).toSeq.map(_._2.head)) // filter duplicate outputs
  )
  .dependsOn(parboiled2)

lazy val testing = libraryProject("testing")
  .settings(
    description := "Instances and laws for testing http4s code",
    libraryDependencies ++= Seq(
      scalacheck
    ),
    macroParadiseSetting
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
  .dependsOn(core, testing % "test->test", server % "test->compile", theDsl % "test->compile", scalaXml % "test->compile")

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
      Http4sPlugin.asyncHttpClient,
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
      Http4sPlugin.argonaut
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
    libraryDependencies += Http4sPlugin.json4sNative
  )
  .dependsOn(json4s % "compile;test->test")

lazy val json4sJackson = libraryProject("json4s-jackson")
  .settings(
    description := "Provides json4s-jackson codecs for http4s",
    libraryDependencies += Http4sPlugin.json4sJackson
  )
  .dependsOn(json4s % "compile;test->test")

lazy val scalaXml = libraryProject("scala-xml")
  .settings(
    description := "Provides scala-xml codecs for http4s",
    libraryDependencies ++= scalaVersion (VersionNumber(_).numbers match {
      case Seq(2, scalaMajor, _*) if scalaMajor >= 11 => Seq(Http4sPlugin.scalaXml)
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
  .enablePlugins(DisablePublishingPlugin)
  .settings(noCoverageSettings)
  .settings(
    description := "Benchmarks for http4s",
    libraryDependencies += circeParser
  )
  .dependsOn(core, circe)

lazy val loadTest = http4sProject("load-test")
  .enablePlugins(DisablePublishingPlugin)
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

val exportMetadataForSite = TaskKey[File]("export-metadata-for-site", "Export build metadata, like http4s and key dependency versions, for use in tuts and when building site")
lazy val docs = http4sProject("docs")
  .enablePlugins(DisablePublishingPlugin)
  .settings(noCoverageSettings)
  .settings(unidocSettings)
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
    tutTargetDirectory := tutTargetDirectory.value / apiVersion.value.productIterator.mkString("v", ".", ""),
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
    makeSite := makeSite.dependsOn(tutQuick, exportMetadataForSite).value,
    baseURL in Hugo := {
      if (isTravisBuild.value) new URI(s"http://http4s.org")
      else new URI(s"http://127.0.0.1:${previewFixedPort.value.getOrElse(4000)}")
    },
    includeFilter in Hugo := (
      "*.html" |
        "*.png" | "*.jpg" | "*.gif" | "*.ico" | "*.svg" |
        "*.js" | "*.swf" | "*.json" | "*.md" |
        "*.css" | "*.woff" | "*.woff2" | "*.ttf" |
        "CNAME" | "_config.yml"
    ),
    siteMappings ++= {
      val m = (mappings in (ScalaUnidoc, packageDoc)).value
      val (major, minor) = apiVersion.value
      for ((f, d) <- m) yield (f, s"v$major.$minor/api/$d")
    }
  )
  .dependsOn(client, core, theDsl, blazeServer, blazeClient, circe)

lazy val examples = http4sProject("examples")
  .enablePlugins(DisablePublishingPlugin)
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
  .settings(
    moduleName := s"http4s-$name",
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2,"showtimes", "failtrace"),
    initCommands()
  )

def libraryProject(name: String) = http4sProject(name)

def exampleProject(name: String) = http4sProject(name)
  .in(file(name.replace("examples-", "examples/")))
  .enablePlugins(DisablePublishingPlugin)
  .settings(noCoverageSettings)
  .dependsOn(examples)

lazy val apiVersion = taskKey[(Int, Int)]("Defines the API compatibility version for the project.")
lazy val jvmTarget = taskKey[String]("Defines the target JVM version for object files.")

lazy val commonSettings = Seq(
  jvmTarget := scalaVersion.map {
    VersionNumber(_).numbers match {
      case Seq(2, 10, _*) => "1.7"
      case _ => "1.8"
    }
  }.value,
  scalacOptions in Compile ++= Seq(
    s"-target:jvm-${jvmTarget.value}"
  ),
  scalacOptions in (Compile, doc) += "-no-link-warnings",
  javacOptions ++= Seq(
    "-source", jvmTarget.value,
    "-target", jvmTarget.value,
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
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
          case e: xml.Elem
              if e.label == "dependency" && e.child.exists(child => child.label == "artifactId" && child.text.contains("parboiled2")) => Nil
          case _ => Seq(node)
        }
      }).transform(node).head
  },
  coursierVerbosity := 0,
  ivyLoggingLevel := UpdateLogging.Quiet // This doesn't seem to work? We see this in MiMa
)

lazy val noCoverageSettings = Seq(
  coverageExcludedPackages := ".*"
)

def initCommands(additionalImports: String*) =
  initialCommands := (List(
    "scalaz._",
    "Scalaz._",
    "scalaz.concurrent.Task",
    "org.http4s._"
  ) ++ additionalImports).mkString("import ", ", ", "")

addCommandAlias("validate", ";test ;makeSite ;mimaReportBinaryIssues")
