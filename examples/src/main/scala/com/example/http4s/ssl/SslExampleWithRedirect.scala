package com.example.http4s
package ssl

import java.nio.file.Paths

import fs2._
import org.http4s.server._
import org.http4s.dsl._
import org.http4s.{HttpService, Uri}
import org.http4s.headers.Host
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.{ SSLKeyStoreSupport, ServerBuilder }
import org.http4s.util.StreamApp

trait SslExampleWithRedirect extends StreamApp {
  val securePort = 8443

  implicit val strategy: Strategy = Strategy.fromFixedDaemonPool(2)

  // TODO: Reference server.jks from something other than one child down.
  val keypath = Paths.get("../server.jks").toAbsolutePath().toString()

  def builder: ServerBuilder with SSLKeyStoreSupport

  val redirectService = HttpService {
    case request =>
      request.headers.get(Host) match {
        case Some(Host(host, _)) =>
          val baseUri = Uri.fromString(s"https://$host:$securePort/").getOrElse(uri("/"))
          MovedPermanently(baseUri.withPath(request.uri.path))
        case _ =>
          BadRequest()
      }
  }

  def sslStream = builder
    .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
    .mountService(ExampleService.service, "/http4s")
    .bindHttp(8443)
    .serve

  def redirectStream = builder
    .mountService(redirectService, "/http4s")
    .bindHttp(8080)
    .serve

  def stream(args: List[String]) = sslStream mergeHaltBoth redirectStream
}
