import org.http4s.build.Http4sPlugin._
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Global settings
organization in ThisBuild := "org.http4s"

// Root project
name := "http4s"
description := "A minimal, Scala-idiomatic library for HTTP"
enablePlugins(PrivateProjectPlugin)

cancelable in Global := true

lazy val core = libraryProject("core")
  .enablePlugins(BuildInfoPlugin)
  .settings(
    description := "Core http4s library for servers and clients",
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      scalaVersion,
      BuildInfoKey.map(http4sApiVersion) { case (_, v) => "apiVersion" -> v }
    ),
    buildInfoPackage := organization.value,
    libraryDependencies ++= Seq(
      cats,
      catsEffect,
      fs2Io,
      http4sWebsocket,
      log4s,
      parboiled,
      scalaReflect(scalaOrganization.value, scalaVersion.value) % "provided",
      scalaCompiler(scalaOrganization.value, scalaVersion.value) % "provided"
    ),
  )

lazy val testing = libraryProject("testing")
  .settings(
    description := "Instances and laws for testing http4s code",
    libraryDependencies ++= Seq(
      catsEffectLaws,
      scalacheck,
      specs2Core
    ),
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

lazy val prometheusServerMetrics = libraryProject("prometheus-server-metrics")
  .settings(
    description := "Support for Prometheus Metrics on the server",
    libraryDependencies ++= Seq(
      prometheusClient,
      prometheusHotspot
    ),
  )
  .dependsOn(server % "compile;test->test", theDsl)

lazy val client = libraryProject("client")
  .settings(
    description := "Base library for building http4s clients",
    libraryDependencies += jettyServlet % "test"
  )
  .dependsOn(
    core,
    testing % "test->test",
    server % "test->compile",
    theDsl % "test->compile",
    scalaXml % "test->compile")

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
      fs2ReactiveStreams
    )
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")

lazy val okHttpClient = libraryProject("okhttp-client")
  .settings(
    description := "okhttp implementation for http4s clients",
    libraryDependencies ++= Seq(
      Http4sPlugin.okhttp
    )
  )
  .dependsOn(core, testing % "test->test", client % "compile;test->test")


lazy val servlet = libraryProject("servlet")
  .settings(
    description := "Portable servlet implementation for http4s servers",
    libraryDependencies ++= Seq(
      javaxServletApi % "provided",
      jettyServer % "test",
      jettyServlet % "test",
      mockito % "test"
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
    libraryDependencies += jawnFs2
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

lazy val boopickle = libraryProject("boopickle")
  .settings(
    description := "Provides Boopickle codecs for http4s",
    libraryDependencies ++= Seq(
      Http4sPlugin.boopickle
    ),
  )
  .dependsOn(core, testing % "test->test")

lazy val circe = libraryProject("circe")
  .settings(
    description := "Provides Circe codecs for http4s",
    libraryDependencies ++= Seq(
      circeJawn,
      circeTesting % "test"
    )
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
    libraryDependencies ++= scalaVersion(VersionNumber(_).numbers match {
      case Seq(2, scalaMajor, _*) if scalaMajor >= 11 => Seq(Http4sPlugin.scalaXml)
      case _ => Seq.empty
    }).value
  )
  .dependsOn(core, testing % "test->test")

lazy val twirl = http4sProject("twirl")
  .settings(
    description := "Twirl template support for http4s",
    libraryDependencies += twirlApi,
    TwirlKeys.templateImports := Nil
  )
  .enablePlugins(SbtTwirl)
  .dependsOn(core, testing % "test->test")

lazy val mimedbGenerator = http4sProject("mimedb-generator")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "MimeDB source code generator",
    libraryDependencies ++= Seq(
      Http4sPlugin.treeHugger,
      Http4sPlugin.circeGeneric
    )
  )
  .dependsOn(blazeClient, circe)

lazy val bench = http4sProject("bench")
  .enablePlugins(JmhPlugin)
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Benchmarks for http4s",
    libraryDependencies += circeParser
  )
  .dependsOn(core, circe)

lazy val loadTest = http4sProject("load-test")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Load tests for http4s servers",
    libraryDependencies ++= Seq(
      gatlingHighCharts,
      gatlingTest
    ).map(_ % "it,test")
  )
  .enablePlugins(GatlingPlugin)

lazy val docs = http4sProject("docs")
  .enablePlugins(
    GhpagesPlugin,
    HugoPlugin,
    PrivateProjectPlugin,
    ScalaUnidocPlugin,
    TutPlugin
  )
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
        examplesDocker,
        examplesJetty,
        examplesTomcat,
        examplesWar,
        mimedbGenerator,
        loadTest
      ),
    scalacOptions in Tut ~= {
      val unwanted = Set("-Ywarn-unused:params", "-Ywarn-unused:imports")
      // unused params warnings are disabled due to undefined functions in the doc
      _.filterNot(unwanted) :+ "-Xfatal-warnings"
    },
    scalacOptions in (Compile, doc) ++= {
      scmInfo.value match {
        case Some(s) =>
          val isMaster = git.gitCurrentBranch.value == "master"
          val isSnapshot =
            git.gitCurrentTags.value.map(git.gitTagToVersionNumber.value).flatten.isEmpty
          val gitHeadCommit = git.gitHeadCommit.value
          val v = version.value
          val path =
            if (isSnapshot && isMaster)
              s"${s.browseUrl}/tree/master€{FILE_PATH}.scala"
            else if (isSnapshot)
              s"${s.browseUrl}/blob/${gitHeadCommit.get}€{FILE_PATH}.scala"
            else
              s"${s.browseUrl}/blob/v${version}€{FILE_PATH}.scala"

          Seq(
            "-implicits",
            "-doc-source-url",
            path,
            "-sourcepath",
            (baseDirectory in ThisBuild).value.getAbsolutePath
          )
        case _ => Seq.empty
      }
    },
    scalacOptions in (Compile, doc) -= "-Ywarn-unused:imports",
    makeSite := makeSite.dependsOn(tutQuick, http4sBuildData).value,
    baseURL in Hugo := {
      val docsPrefix = extractDocsPrefix(version.value)
      if (isTravisBuild.value) new URI(s"https://http4s.org${docsPrefix}")
      else new URI(s"http://127.0.0.1:${previewFixedPort.value.getOrElse(4000)}${docsPrefix}")
    },
    siteMappings := {
      val docsPrefix = extractDocsPrefix(version.value)
      for ((f, d) <- siteMappings.value) yield (f, docsPrefix + "/" + d)
    },
    siteMappings ++= {
      val docsPrefix = extractDocsPrefix(version.value)
      for ((f, d) <- (mappings in (ScalaUnidoc, packageDoc)).value)
        yield (f, s"$docsPrefix/api/$d")
    },
    includeFilter in ghpagesCleanSite := {
      new FileFilter {
        val docsPrefix = extractDocsPrefix(version.value)
        def accept(f: File) =
          f.getCanonicalPath.startsWith(
            (ghpagesRepository.value / s"${docsPrefix}").getCanonicalPath)
      }
    }
  )
  .dependsOn(client, core, theDsl, blazeServer, blazeClient, circe)

lazy val website = http4sProject("website")
  .enablePlugins(HugoPlugin, GhpagesPlugin, PrivateProjectPlugin)
  .settings(
    description := "Common area of http4s.org",
    baseURL in Hugo := {
      if (isTravisBuild.value) new URI(s"https://http4s.org")
      else new URI(s"http://127.0.0.1:${previewFixedPort.value.getOrElse(4000)}")
    },
    makeSite := makeSite.dependsOn(http4sBuildData).value,
    // all .md|markdown files go into `content` dir for hugo processing
    ghpagesNoJekyll := true,
    excludeFilter in ghpagesCleanSite :=
      new FileFilter {
        val v = ghpagesRepository.value.getCanonicalPath + "/v"
        def accept(f: File) =
          f.getCanonicalPath.startsWith(v) &&
            f.getCanonicalPath.charAt(v.size).isDigit
      }
  )

lazy val examples = http4sProject("examples")
  .enablePlugins(PrivateProjectPlugin)
  .settings(
    description := "Common code for http4s examples",
    libraryDependencies ++= Seq(
      circeGeneric,
      logbackClassic % "runtime",
      jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
    ),
    TwirlKeys.templateImports := Nil
  )
  .dependsOn(server, serverMetrics, theDsl, circe, scalaXml, twirl)
  .enablePlugins(SbtTwirl)

lazy val examplesBlaze = exampleProject("examples-blaze")
  .settings(Revolver.settings)
  .settings(
    description := "Examples of http4s server and clients on blaze",
    fork := true,
    libraryDependencies ++= Seq(alpnBoot, metricsJson),
    javaOptions in run ++= addAlpnPath((managedClasspath in Runtime).value)
  )
  .dependsOn(blazeServer, blazeClient)

lazy val examplesDocker = http4sProject("examples-docker")
  .in(file("examples/docker"))
  .enablePlugins(JavaAppPackaging, DockerPlugin, PrivateProjectPlugin)
  .settings(
    description := "Builds a docker image for a blaze-server",
    packageName in Docker := "http4s/blaze-server",
    maintainer in Docker := "http4s",
    dockerUpdateLatest := true,
    dockerExposedPorts := List(8080),
    libraryDependencies ++= Seq(
      logbackClassic % "runtime"
    )
  )
  .dependsOn(blazeServer, theDsl)

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

def http4sProject(name: String) =
  Project(name, file(name))
    .settings(commonSettings)
    .settings(
      moduleName := s"http4s-$name",
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "showtimes", "failtrace"),
      initCommands()
    )

def libraryProject(name: String) = http4sProject(name)

def exampleProject(name: String) =
  http4sProject(name)
    .in(file(name.replace("examples-", "examples/")))
    .enablePlugins(PrivateProjectPlugin)
    .dependsOn(examples)

lazy val commonSettings = Seq(
  http4sJvmTarget := scalaVersion.map {
    VersionNumber(_).numbers match {
      case Seq(2, 10, _*) => "1.7"
      case _ => "1.8"
    }
  }.value,
  scalacOptions in Compile ++= Seq(
    s"-target:jvm-${http4sJvmTarget.value}"
  ),
  scalacOptions in (Compile, doc) += "-no-link-warnings",
  javacOptions ++= Seq(
    "-source",
    http4sJvmTarget.value,
    "-target",
    http4sJvmTarget.value,
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  ),
  libraryDependencies ++= Seq(
    catsLaws,
    catsKernelLaws,
    discipline,
    logbackClassic,
    scalacheck, // 0.13.3 fixes None.get
    specs2Core,
    specs2MatcherExtra,
    specs2Scalacheck
  ).map(_ % "test"),
  // don't include scoverage as a dependency in the pom
  // https://github.com/scoverage/sbt-scoverage/issues/153
  // this code was copied from https://github.com/mongodb/mongo-spark
  pomPostProcess := { (node: xml.Node) =>
    new RuleTransformer(new RewriteRule {
      override def transform(node: xml.Node): Seq[xml.Node] = node match {
        case e: xml.Elem
            if e.label == "dependency" && e.child.exists(
              child => child.label == "groupId" && child.text == "org.scoverage") =>
          Nil
        case _ => Seq(node)
      }
    }).transform(node).head
  },
  ivyLoggingLevel := UpdateLogging.Quiet, // This doesn't seem to work? We see this in MiMa
  git.remoteRepo := "git@github.com:http4s/http4s.git",
  includeFilter in Hugo := (
    "*.html" | "*.png" | "*.jpg" | "*.gif" | "*.ico" | "*.svg" |
      "*.js" | "*.swf" | "*.json" | "*.md" |
      "*.css" | "*.woff" | "*.woff2" | "*.ttf" |
      "CNAME" | "_config.yml" | "_redirects"
  )
)

def initCommands(additionalImports: String*) =
  initialCommands := (List(
    "fs2._",
    "cats._",
    "cats.data._",
    "cats.effect._",
    "cats.implicits._"
  ) ++ additionalImports).mkString("import ", ", ", "")

// Everything is driven through release steps and the http4s* variables
// This won't actually release unless on Travis.
addCommandAlias("ci", ";clean ;release with-defaults")
