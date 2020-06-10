/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal

import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, Headers, MediaType, Message, Request, Response}

object Logger {

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

    val headers: String =
      if (logHeaders)
        message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
      else ""

    val bodyText: F[String] = logBody match {
      case true =>
        val m: Stream[F, String] =
          if (isText)
            message
              .bodyAsText(charset.getOrElse(Charset.`UTF-8`))
          else
            message.body
              .map(b => java.lang.Integer.toHexString(b & 0xff))

        m.compile.string
          .map(text => s"""body="$text"""")

      case false =>
        F.pure("")
    }

    def spaced(x: String): String = if (x.isEmpty) x else s" $x"

    bodyText
      .map(body => s"$prelude${spaced(headers)}${spaced(body)}")
      .flatMap(log)
  }

}
