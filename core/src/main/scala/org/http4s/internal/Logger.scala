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
    val logBodyText: Option[String => F[String]] =
      if (logBody) Some(F.pure) else None

    logMessageWithBodyText[F, A](message)(logHeaders, logBodyText, redactHeadersWhen)(log)
  }

  def logMessageWithBodyText[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBodyText: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {
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

    val bodyText: F[String] =
      logBodyText(message.body) match {
        case Some(textF) => textF.map(text => s"""body="$text"""")
        case None => F.pure("")
      }

    def spaced(x: String): String = if (x.isEmpty) x else s" $x"

    bodyText
      .map(body => s"$prelude${spaced(headers)}${spaced(body)}")
      .flatMap(log)
  }

}
