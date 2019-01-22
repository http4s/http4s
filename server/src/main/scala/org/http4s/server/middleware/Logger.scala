package org.http4s
package server
package middleware

import cats.implicits._
import cats.data.Kleisli
import cats.effect._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  private[this] val logger = getLogger

  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(@deprecatedName('httpService) http: Kleisli[F, Request[F], Response[F]])
    : Kleisli[F, Request[F], Response[F]] = {
    val log: String => F[Unit] = s => Sync[F].delay(logger.info(s))
    ResponseLogger(logHeaders, logBody, redactHeadersWhen, log.pure[Option])(
      RequestLogger(logHeaders, logBody, redactHeadersWhen, log.pure[Option])(http)
    )
  }

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {

    val charset = message.charset
    val isBinary = message.contentType.exists(_.mediaType.binary)
    val isJson = message.contentType.exists(mT =>
      mT.mediaType == MediaType.application.json || mT.mediaType == MediaType.application.`vnd.hal+json`)

    val isText = !isBinary || isJson

    def prelude = message match {
      case Request(method, uri, httpVersion, _, _, _) =>
        s"$httpVersion $method $uri"

      case Response(status, httpVersion, _, _, _) =>
        s"$httpVersion $status"
    }

    val headers =
      if (logHeaders)
        message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
      else ""

    val bodyStream = if (logBody && isText) {
      message.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
    } else if (logBody) {
      message.body
        .fold(new StringBuilder)((sb, b) => sb.append(java.lang.Integer.toHexString(b & 0xff)))
        .map(_.toString)
    } else {
      Stream.empty.covary[F]
    }

    val bodyText = if (logBody) {
      bodyStream.fold("")(_ + _).map(text => s"""body="$text"""")
    } else {
      Stream("").covary[F]
    }

    if (!logBody && !logHeaders) F.unit
    else {
      bodyText
        .map(body => s"$prelude $headers $body")
        .evalMap(text => log(text))
        .compile
        .drain
    }
  }
}
