package org.http4s.build

import sbt._
import sbt.Keys._
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

object CentralRequirementsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = Sonatype

  override lazy val projectSettings = Seq(
    sonatypeProfileName := "org.http4s",
    developers ++= List(
      // n.b. alphabetical by GitHub username
      Developer("aeons"                , "Bjørn Madsen"          , "bm@aeons.dk"                      , url("https://github.com/aeons")),
      Developer("before"               , "André Rouel"           , ""                                 , url("https://github.com/before")),
      Developer("bfritz"               , "Brad Fritz"            , ""                                 , url("https://github.com/bfritz")),
      Developer("bryce-anderson"       , "Bryce L. Anderson"     , "bryce.anderson22@gmail.com"       , url("https://github.com/bryce-anderson")),
      Developer("casualjim"            , "Ivan Porto Carrero"    , "ivan@flanders.co.nz"              , url("https://github.com/casualjim")),
      Developer("cencarnacion"         , "Carlos Encarnacion"    , ""                                 , url("https://github.com/cencarnacion")),
      Developer("ChristopherDavenport" , "Christopher Davenport" , "chris@christopherdavenport.tech"  , url("https://github.com/ChristopherDavenport")),
      Developer("cquiroz"              , "Carlos Quiroz"         , ""                                 , url("https://github.com/cquiroz")),
      Developer("hvesalai"             , "Heikki Vesalainen"     , ""                                 , url("https://github.com/hvesalai")),
      Developer("jcranky"              , "Paulo Siqueira"        , ""                                 , url("https://github.com/jcranky")),
      Developer("jedesah"              , "Jean-Rémi Desjardins"  , ""                                 , url("https://github.com/jedesah")),
      Developer("jmcardon"             , "Jose Cardona"          , ""                                 , url("https://github.com/jmcardon")),      
      Developer("julien-truffaut"      , "Julien Truffaut"       , ""                                 , url("https://github.com/julien-truffaut")),
      Developer("kryptt"               , "Rodolfo Hansen"        , ""                                 , url("https://github.com/kryptt")),
      Developer("reactormonk"          , "Simon Hafner"          , ""                                 , url("https://github.com/reactormonk")),
      Developer("refried"              , "Arya Irani"            , ""                                 , url("https://github.com/refried")),
      Developer("rossabaker"           , "Ross A. Baker"         , "ross@rossabaker.com"              , url("https://github.com/rossabaker")),
      Developer("shengc"               , "Sheng Chen"            , ""                                 , url("https://github.com/shengc")),
      Developer("SystemFw"             , "Fabio Labella"         , ""                                 , url("https://github.com/SystemFw")),
    ),
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("https://http4s.org/")),
    scmInfo := Some(ScmInfo(url("https://github.com/http4s/http4s"), "git@github.com:http4s/http4s.git")),
    startYear := Some(2013),
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    Compile / packageBin / publishArtifact := true,
    Compile / packageSrc / publishArtifact := true,
    Test / publishArtifact := false,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= (for {
      username <- sys.env.get("SONATYPE_USERNAME")
      password <- sys.env.get("SONATYPE_PASSWORD")
    } yield Credentials(
        "Sonatype Nexus Repository Manager",
        "oss.sonatype.org",
        username, password
      )
    ).toSeq
  )
}
