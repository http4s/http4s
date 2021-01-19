/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal

import cats.effect.Sync
import cats.syntax.all._
import fs2.Stream
import org.http4s.{Charset, Headers, MediaType, Message, Request, Response}
import org.typelevel.ci.CIString

object Logger {

  def defaultLogHeaders[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains): String =
    if (logHeaders)
      message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
    else ""

  def defaultLogBody[F[_]: Sync, A <: Message[F]](message: A)(logBody: Boolean): Option[F[String]] =
    if (logBody) {
      val isBinary = message.contentType.exists(_.mediaType.binary)
      val isJson = message.contentType.exists(mT =>
        mT.mediaType == MediaType.application.json || mT.mediaType.subType.endsWith("+json"))
      val bodyStream = if (!isBinary || isJson) {
        message.bodyText(implicitly, message.charset.getOrElse(Charset.`UTF-8`))
      } else {
        message.body.map(b => java.lang.Integer.toHexString(b & 0xff))
      }
      Some(bodyStream.compile.string)
    } else None

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {

    val logBodyText = (_: Stream[F, Byte]) => defaultLogBody[F, A](message)(logBody)

    logMessageWithBodyText[F, A](message)(logHeaders, logBodyText, redactHeadersWhen)(log)
  }

  def logMessageWithBodyText[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBodyText: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {
    def prelude =
      message match {
        case Request(method, uri, httpVersion, _, _, _) => s"$httpVersion $method $uri"
        case Response(status, httpVersion, _, _, _) => s"$httpVersion $status"
      }

    val headers: String = defaultLogHeaders[F, A](message)(logHeaders, redactHeadersWhen)

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
