import sbtunidoc.Plugin.UnidocKeys._

lazy val core = project

lazy val server = project.dependsOn(core % "compile;test->test")

lazy val client = project.dependsOn(core % "compile;test->test", server % "test->compile")

lazy val `blaze-core` = project.dependsOn(core)

lazy val `blaze-server` = project.dependsOn(`blaze-core` % "compile;test->test", server)

lazy val `blaze-client` = project.dependsOn(`blaze-core` % "compile;test->test", client % "compile;test->test")

lazy val servlet = project.dependsOn(server % "compile;test->test")

lazy val jetty = project.dependsOn(servlet)

lazy val tomcat = project.dependsOn(servlet)

// The name `dsl` clashes with modern sbt
lazy val theDsl = Project("dsl", file("dsl")).dependsOn(core % "compile;test->test", server % "test->compile")

lazy val jawn = project.dependsOn(core % "compile;test->test")

lazy val json4s = project.dependsOn(jawn % "compile;test->test")

lazy val `json4s-native` = project.dependsOn(json4s, json4s % "compile;test->test")

lazy val `json4s-jackson` = project.dependsOn(json4s, json4s % "compile;test->test")

lazy val argonaut = project.dependsOn(core % "compile;test->test", jawn % "compile;test->test")

lazy val `scala-xml` = project.dependsOn(core % "compile;test->test")

// The plugin must be enabled for the tests
lazy val twirl = project.dependsOn(core % "compile;test->test").enablePlugins(SbtTwirl)

lazy val examples = project.dependsOn(server, theDsl, argonaut, `scala-xml`, twirl).enablePlugins(SbtTwirl)

lazy val `examples-blaze` = Project("examples-blaze", file("examples/blaze")).dependsOn(examples, `blaze-server`, `blaze-client`)

lazy val `examples-jetty` = Project("examples-jetty", file("examples/jetty")).dependsOn(examples, jetty)

lazy val `examples-tomcat` = Project("examples-tomcat", file("examples/tomcat")).dependsOn(examples, tomcat)

lazy val `examples-war` = Project("examples-war", file("examples/war")).dependsOn(examples, servlet)

organization in ThisBuild := "org.http4s"

name := "http4s"

version in ThisBuild := "0.7.0-SNAPSHOT"

apiVersion in ThisBuild <<= version.map(extractApiVersion)

description := "A minimal, Scala-idiomatic library for HTTP"

homepage in ThisBuild := Some(url("https://github.com/http4s/http4s"))

startYear in ThisBuild := Some(2013)

licenses in ThisBuild := Seq(
  "Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
)

scmInfo in ThisBuild := {
  val base = "github.com/http4s/http4s"
  Some(ScmInfo(url(s"https://$base"), s"scm:git:https://$base", Some(s"scm:git:git@$base")))
}

pomExtra in ThisBuild := (
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
  </developers>
)

scalaVersion in ThisBuild := "2.10.4"

crossScalaVersions in ThisBuild := Seq(
  "2.10.4",
  "2.11.5"
)

val JvmTarget = "1.7"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  s"-target:jvm-${JvmTarget}",
  "-unchecked",
  "-Xlint"
)

javacOptions in ThisBuild ++= Seq(
  "-source", JvmTarget,
  "-target", JvmTarget,
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

resolvers in ThisBuild ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
  "rossabaker Bintray Repo" at "http://dl.bintray.com/rossabaker/maven"
)

/* These test dependencies applied to all projects under the http4s umbrella */
libraryDependencies in ThisBuild ++= Seq(
  scalameter % "test",
  scalazScalacheckBinding % "test",
  scalazSpecs2 % "test"
)

logLevel := Level.Warn

ivyLoggingLevel in (ThisBuild, update) := UpdateLogging.DownloadOnly

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version(v => Some(nexusRepoFor(v)))

publishArtifact in (ThisBuild, Test) := false

// Don't publish root pom.  It's not needed.
packagedArtifacts in file(".") := Map.empty

credentials ++= travisCredentials.toSeq

unidocSettings

unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(examples)

// autoAPIMappings is not respected by Unidoc
apiMappings in ThisBuild += (scalaInstance.value.libraryJar -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"))

Http4sSite.settings
