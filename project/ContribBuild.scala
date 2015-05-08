import sbt._
import Keys._

import scala.util.Properties.envOrNone

object ContribBuild extends Build {

  val http4sVersion = "0.7.0"

  def isSnapshot(version: String): Boolean = version.endsWith("-SNAPSHOT")

  lazy val `http4s-core`        = "org.http4s"           %% "http4s-core"          % http4sVersion
  lazy val `http4s-server`      = "org.http4s"           %% "http4s-server"        % http4sVersion
  lazy val `http4s-client`      = "org.http4s"           %% "http4s-client"        % http4sVersion
  lazy val `http4s-dsl`         = "org.http4s"           %% "http4s-dsl"           % http4sVersion 
  lazy val `http4s-json-native` = "org.http4s"           %% "http4s-json4s-native" % http4sVersion
}

