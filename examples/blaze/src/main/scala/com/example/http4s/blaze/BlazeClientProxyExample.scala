package com.example.http4s.blaze

import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.blaze.{ BlazeClientConfig, PooledHttp1Client }
import org.http4s.syntax.all._
import org.http4s.util.CaseInsensitiveString
import scalaz.concurrent.Task

object BlazeClientProxyExample extends App {
  // Configure the proxy for http requests
  sys.props("http.proxyHost") = "localhost"
  sys.props("http.proxyPort") = "8888"

  // Configure the proxy for https requests
  sys.props("https.proxyHost") = "localhost"
  sys.props("https.proxyPort") = "443"

  // This applies to both
  sys.props("http.nonProxyHosts") = "localhost|127.*|[::1]"

  val config = BlazeClientConfig.defaultConfig

  val client = PooledHttp1Client(config = config)
  println("Secure request with proxy")
  client.get("https://www.aa.com/") { case resp => resp.as[String].map(_.size) }.map(l => println(l + " bytes")).attempt.unsafePerformSync
  println("Insecure request with proxy")
  client.get("http://airborne.gogoinflight.com/") { case resp => resp.as[String].map(_.size) }.map(l => println(l + " bytes")).attempt.unsafePerformSync
  println("Skip proxy")
  client.get("http://localhost:8081/") { case resp => resp.as[String].map(_.size) }.map(l => println(l + " bytes")).attempt.unsafePerformSync
  client.shutdownNow()
}
