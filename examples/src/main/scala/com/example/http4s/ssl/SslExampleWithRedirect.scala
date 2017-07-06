package com.example.http4s
package ssl

import java.nio.file.Paths
import java.util.concurrent.Executors

import cats.effect.IO
import cats.syntax.option._
import fs2._
import org.http4s.HttpService
import org.http4s.Uri.{Authority, RegName}
import org.http4s.dsl._
import org.http4s.headers.Host
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.{SSLKeyStoreSupport, ServerBuilder}
import org.http4s.util.StreamApp

import scala.concurrent.ExecutionContext

trait SslExampleWithRedirect extends StreamApp[IO] {
  val securePort = 8443

  implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  // TODO: Reference server.jks from something other than one child down.
  val keypath = Paths.get("../server.jks").toAbsolutePath.toString

  def builder: ServerBuilder[IO] with SSLKeyStoreSupport[IO]

  val redirectService = HttpService[IO] {
    case request =>
      request.headers.get(Host) match {
        case Some(Host(host, _)) =>
          val baseUri = request.uri.copy(scheme = "https".ci.some, authority = Some(Authority(request.uri.authority.flatMap(_.userInfo), RegName(host), port = securePort.some)))
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

  def stream(args: List[String]): Stream[IO, Nothing] = sslStream mergeHaltBoth redirectStream
}
