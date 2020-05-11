package org.http4s
package server
package middleware

import cats.~>
import cats.arrow.FunctionK
import cats.implicits._
import cats.data.OptionT
import cats.effect.{Bracket, Concurrent, Sync}
import cats.effect.Sync._
import fs2.Stream
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  private[this] val logger = getLogger

  def apply[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(http: Http[G, F])(implicit G: Bracket[G, Throwable], F: Concurrent[F]): Http[G, F] = {
    val log: String => F[Unit] = logAction.getOrElse { s =>
      Sync[F].delay(logger.info(s))
    }
    ResponseLogger(logHeaders, logBody, fk, redactHeadersWhen, log.pure[Option])(
      RequestLogger(logHeaders, logBody, fk, redactHeadersWhen, log.pure[Option])(http)
    )
  }

  def httpApp[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpRoutes[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {
    val charset = message.charset
    val isBinary = message.contentType.exists(_.mediaType.binary)
    val isJson = message.contentType.exists(mT =>
      mT.mediaType == MediaType.application.json || mT.mediaType.subType.endsWith("+json"))

    val isText = !isBinary || isJson

    def prelude =
      message match {
        case Request(method, uri, httpVersion, _, _, _) =>
          s"$httpVersion $method $uri"

        case Response(status, httpVersion, _, _, _) =>
          s"$httpVersion $status"
      }

    val headers =
      if (logHeaders)
        message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
      else ""

    val bodyStream =
      if (logBody && isText)
        message.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
      else if (logBody)
        message.body
          .map(b => java.lang.Integer.toHexString(b & 0xff))
      else
        Stream.empty.covary[F]

    val bodyText =
      if (logBody)
        bodyStream.compile.string
          .map(text => s"""body="$text"""")
      else
        F.pure("")

    def spaced(x: String): String = if (x.isEmpty) x else s" $x"

    bodyText
      .map(body => s"$prelude${spaced(headers)}${spaced(body)}")
      .flatMap(log)
  }
}
