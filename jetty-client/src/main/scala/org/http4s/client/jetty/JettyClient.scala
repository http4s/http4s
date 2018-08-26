package org.http4s
package client
package jetty

import cats.data.Kleisli
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.{Request => JettyRequest}
import org.eclipse.jetty.http.{HttpVersion => JHttpVersion}
import org.log4s.{Logger, getLogger}
import scala.concurrent.ExecutionContext

object JettyClient {

  private val logger: Logger = getLogger

  def apply[F[_]](client: HttpClient = new HttpClient())(
      implicit F: ConcurrentEffect[F],
      ec: ExecutionContext): F[Client[F]] =
    F.pure(client)
      .flatTap(client => F.delay { client.start() })
      .map(client =>
        Client(
          Kleisli { req =>
            F.asyncF[DisposableResponse[F]] { cb =>
              F.bracket(StreamRequestContentProvider()) { dcp =>
                val jReq = toJettyRequest(client, req, dcp)
                for {
                  rl <- ResponseListener(cb)
                  _ <- F.delay(jReq.send(rl))
                  _ <- dcp.write(req)
                } yield ()
              } { dcp =>
                F.delay(dcp.close())
              }
            }
          },
          F.delay(client.stop())
            .handleErrorWith(t => F.delay(logger.error(t)("Unable to shut down Jetty client")))
      ))

  def stream[F[_]](client: HttpClient = new HttpClient())(
      implicit F: ConcurrentEffect[F],
      ec: ExecutionContext): Stream[F, Client[F]] =
    Stream.bracket(apply(client))(_.shutdown)

  private def toJettyRequest[F[_]](
      client: HttpClient,
      request: Request[F],
      dcp: StreamRequestContentProvider[F]): JettyRequest = {
    val jReq = client
      .newRequest(request.uri.toString)
      .method(request.method.name)
      .version(
        request.httpVersion match {
          case HttpVersion.`HTTP/1.1` => JHttpVersion.HTTP_1_1
          case HttpVersion.`HTTP/2.0` => JHttpVersion.HTTP_2
          case HttpVersion.`HTTP/1.0` => JHttpVersion.HTTP_1_0
          case _ => JHttpVersion.HTTP_1_1
        }
      )

    for (h <- request.headers) jReq.header(h.name.toString, h.value)
    jReq.content(dcp)
  }

}
