package com.example.http4s.blaze

import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.blaze.{ BlazeClientConfig, PooledHttp1Client, ProxyConfig }
import org.http4s.syntax.all._
import org.http4s.util.CaseInsensitiveString
import scalaz.concurrent.Task

object BlazeClientProxyExample extends App {
  // Set up a proxy.  tinyproxy is good for this.
  val scheme = "http"
  val host = "localhost"
  val port = 8888

  val config = BlazeClientConfig.defaultConfig.withProxy {
    case _ => ProxyConfig(
      scheme.ci,
      Authority(host = RegName(host.ci), port = Some(port)),
      None
    )
  }

  val client = PooledHttp1Client(config = config)
  print("Getting status of http://http4s.org/ through proxy: ")
  client.get("http://http4s.org/") { case resp => Task.now(resp.status) }.map(println).unsafePerformSync
  print("Getting status of https://github.com/ through proxy: ")
  client.get("https://github.com/") { case resp => Task.now(resp.status) }.map(println).unsafePerformSync
  client.shutdownNow()
}
