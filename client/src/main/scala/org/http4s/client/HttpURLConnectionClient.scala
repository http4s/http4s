package org.http4s
package client

import cats.data.Kleisli
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.io.{readInputStream, writeOutputStream}
import java.net.{HttpURLConnection, URL}
import scala.collection.JavaConverters._

/** A [[Client]] based on `java.net.HttpUrlConnection` */
object HttpURLConnectionClient {

  /** Construct a new Client */
  def apply[F[_]](implicit F: Effect[F]): Client[F] =
    Client(
      Kleisli { req =>
        for {
          url <- F.delay(new URL(req.uri.toString))
          conn <- F.delay(url.openConnection().asInstanceOf[HttpURLConnection])
          _ <- F.delay(conn.setRequestMethod(req.method.renderString))
          _ <- F.delay(req.headers.foreach {
            case Header(name, value) => conn.setRequestProperty(name.value, value)
          })
          _ <- F.delay(conn.setInstanceFollowRedirects(false))
          _ <- F.delay(conn.setDoInput(true))
          _ <- writeBody(conn, req)
          status <- F.fromEither(Status.fromInt(conn.getResponseCode))
          headers <- F.delay(Headers(
            conn.getHeaderFields.asScala
              .filter(_._1 != null)
              .flatMap { case (k, vs) => vs.asScala.map(Header(k, _)) }
              .toList
          ))
          body = readInputStream(F.delay(conn.getInputStream), 4096)
        } yield DisposableResponse(
          Response(status = status, headers = headers, body = body),
          F.delay(conn.getInputStream.close())
        )
      },
      F.unit
    )

  private def writeBody[F[_]](conn: HttpURLConnection, req: Request[F])(implicit F: Sync[F]): F[Unit] =
    if (req.isChunked) {
      F.delay(conn.setDoOutput(true)) *>
      F.delay(conn.setChunkedStreamingMode(4096)) *>
      req.body.to(writeOutputStream(F.delay(conn.getOutputStream), false)).compile.drain
    } else req.contentLength match {
      case Some(len) if len >= 0L =>
        F.delay(conn.setDoOutput(true)) *>
        F.delay(conn.setFixedLengthStreamingMode(len)) *>
        req.body.to(writeOutputStream(F.delay(conn.getOutputStream), false)).compile.drain
      case _ =>
        F.delay(conn.setDoOutput(false))
    }
}
