package com.example.http4s
package ssl

import cats.effect.Effect
import cats.syntax.option._
import fs2._
import java.nio.file.Paths
import org.http4s.HttpService
import org.http4s.Uri.{Authority, RegName}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Host
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.{SSLKeyStoreSupport, ServerBuilder}
import org.http4s.util.StreamApp
import scala.concurrent.ExecutionContext

abstract class SslExampleWithRedirect[F[_]: Effect] extends StreamApp[F] with Http4sDsl[F] {
  val securePort = 8443

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  // TODO: Reference server.jks from something other than one child down.
  val keypath: String = Paths.get("../server.jks").toAbsolutePath.toString

  def builder: ServerBuilder[F] with SSLKeyStoreSupport[F]

  val redirectService: HttpService[F] = HttpService[F] {
    case request =>
      request.headers.get(Host) match {
        case Some(Host(host, _)) =>
          val baseUri = request.uri.copy(
            scheme = "https".ci.some,
            authority = Some(
              Authority(
                request.uri.authority.flatMap(_.userInfo),
                RegName(host),
                port = securePort.some)))
          MovedPermanently(baseUri.withPath(request.uri.path))
        case _ =>
          BadRequest()
      }
  }

  def sslStream(implicit scheduler: Scheduler): Stream[F, Nothing] =
    builder
      .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
      .mountService(new ExampleService[F].service, "/http4s")
      .bindHttp(8443)
      .serve

  def redirectStream: Stream[F, Nothing] =
    builder
      .mountService(redirectService, "/http4s")
      .bindHttp(8080)
      .serve

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, Nothing] =
    Scheduler[F](corePoolSize = 2).flatMap { implicit scheduler =>
      sslStream.mergeHaltBoth(redirectStream)
    }
}
