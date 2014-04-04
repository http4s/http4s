import Http4sDependencies._
import UnidocKeys._
import scala.util.{Properties, Success, Try}

lazy val core = project

lazy val blaze = project.dependsOn(core)

lazy val servlet = project.dependsOn(core)

lazy val dsl = project.dependsOn(core)

lazy val examples = project.dependsOn(servlet, blaze, dsl)

/* common dependencies */
libraryDependencies in ThisBuild ++= Seq(
  junit % "test",
  scalameter % "test",
  scalatest % "test"
)

/* basic project info */
name := "http4s"

organization in ThisBuild := "org.http4s"

version in ThisBuild := "0.1.0-SNAPSHOT"

description := "Common HTTP framework for Scala"

homepage in ThisBuild := Some(url("https://github.com/http4s/http4s"))

startYear in ThisBuild := Some(2013)

licenses in ThisBuild := Seq(
  ("BSD 2-clause", url("https://raw.github.com/http4s/http4s/develop/LICENSE"))
)

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/http4s/http4s"),
    "scm:git:https://github.com/http4s/http4s.git",
    Some("scm:git:git@github.com:http4s/http4s.git")
  )
)

/* scala versions and options */
scalaVersion in ThisBuild := "2.10.4"

offline in ThisBuild := false

scalacOptions in ThisBuild ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds"
)

javacOptions in ThisBuild ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

resolvers in ThisBuild ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  "spray repo" at "http://repo.spray.io",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
)

testOptions in ThisBuild += Tests.Argument(TestFrameworks.Specs2, "console", "junitxml")

/* sbt behavior */
logLevel in (ThisBuild, compile) := Level.Warn

traceLevel in ThisBuild := 5

unidocSettings

unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(examples)

/* publishing */
publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("-SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

Seq("SONATYPE_USER", "SONATYPE_PASS") map Properties.envOrNone match {
  case Seq(Some(user), Some(pass)) =>
    credentials in ThisBuild += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
  case _ =>
    credentials in ThisBuild ~= identity
}

publishArtifact in (ThisBuild, Test) := false

// Don't publish root pom.  It's not needed.
packagedArtifacts in file(".") := Map.empty

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild := (
  <developers>
    <developer>
      <id>rossabaker</id>
      <name>Ross A. Baker</name>
      <email>baker@alumni.indiana.edu</email>
      <!-- <url></url> -->
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
      <!-- <url></url> -->
    </developer>
  </developers>
)

site.settings

site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api/0.1")

includeFilter in SiteKeys.makeSite := "*" -- "*~"

ghpages.settings

git.remoteRepo in ThisBuild := 
  Try(sys.env("GH_TOKEN"))
    .map(token => s"https://${token}@github.com/http4s/http4s.git")
    .getOrElse("git@github.com:http4s/http4s.git")
