package org.http4s
package server
package middleware

import cats.effect._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s.{Logger => SLogger}
import scodec.bits.ByteVector

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply[F[_]: Effect](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(@deprecatedName('httpService, "0.19") httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    ResponseLogger(logHeaders, logBody, redactHeadersWhen)(
      RequestLogger(logHeaders, logBody, redactHeadersWhen)(
        httpRoutes
      )
    )

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      logger: SLogger)(implicit F: Effect[F]): F[Unit] = {

    val charset = message.charset
    val isBinary = message.contentType.exists(_.mediaType.binary)
    val isJson = message.contentType.exists(mT =>
      mT.mediaType == MediaType.`application/json` || mT.mediaType == MediaType.`application/hal+json`)

    val isText = !isBinary || isJson

    val headers =
      if (logHeaders)
        message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
      else ""

    val bodyStream = if (logBody && isText) {
      message.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
    } else if (logBody) {
      message.body.map(ByteVector.fromByte).map(_.toHex)
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
        .map(body => s"$headers $body")
        .map(text => logger.info(text))
        .compile
        .drain
    }
  }
}
