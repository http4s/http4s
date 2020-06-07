/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    ResponseLogger.apply(logHeaders, logBody, redactHeadersWhen, logAction)(
      RequestLogger.apply(logHeaders, logBody, redactHeadersWhen, logAction)(
        client
      )
    )

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {

    val logBodyText: Option[String => F[String]] =
      if (logBody) Some(F.pure) else None

    logMessageWithBodyText[F, A](message)(logHeaders, logBodyText, redactHeadersWhen)(log)
  }

  def logMessageWithBodyText[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBodyText: Option[String => F[String]],
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

    val headers: String =
      if (logHeaders)
        message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
      else ""

    val bodyText: F[String] = logBodyText match {
      case Some(f) =>
        val m: Stream[F, String] =
          if (isText)
            message
              .bodyAsText(charset.getOrElse(Charset.`UTF-8`))
          else
            message.body
              .map(b => java.lang.Integer.toHexString(b & 0xff))

        m.compile.string
          .flatMap(f)
          .map(text => s"""body="$text"""")

      case None =>
        F.pure("")
    }

    def spaced(x: String): String = if (x.isEmpty) x else s" $x"

    bodyText
      .map(body => s"$prelude${spaced(headers)}${spaced(body)}")
      .flatMap(log)
  }
}
