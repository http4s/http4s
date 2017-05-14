package com.example.http4s.blaze

import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.RequestKey
import org.http4s.client.blaze.{ BlazeClientConfig, PooledHttp1Client, ProxyConfig }
import org.http4s.syntax.all._
import org.http4s.util.CaseInsensitiveString
import scalaz.concurrent.Task

object BlazeClientProxyExample extends App {
  val config = BlazeClientConfig.defaultConfig.withProxy {
    case RequestKey(scheme, _) if scheme == "http".ci =>
      ProxyConfig("http".ci, Authority(host = RegName("localhost".ci), port = Some(8888)), None)
    case RequestKey(scheme, _) if scheme == "https".ci =>
      ProxyConfig("http".ci, Authority(host = RegName("localhost".ci), port = Some(8888)), None)
  }

  val client = PooledHttp1Client(config = config)
  client.get("https://github.com/") { case resp => resp.as[String].map(_.size) }.map(l => println(l + " bytes")).unsafePerformSync
  println("Reading from http4s.org with proxy")
  client.get("http://http4s.org/") { case resp => resp.as[String].map(_.size) }.map(l => println(l + " bytes")).unsafePerformSync
  client.shutdownNow()
}
