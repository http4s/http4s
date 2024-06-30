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

import cats.Applicative
import cats.effect.kernel.Concurrent
import cats.syntax.all._
import fs2.text
import org.http4s.{Charset, Entity, Headers, MediaType, Method, Request, Uri}
import org.typelevel.ci.CIString
import scodec.bits.ByteVector

private[http4s] object CurlConverter {
  private def newline: String = " \\\n  "

  private def prepareMethodName(method: Method): String =
    s"$newline--request ${method.name}"

  private def prepareUri(uri: Uri): String =
    s"$newline--url ${PosixQuoting.quote(uri.renderString)}"

  private def prepareHeaders(headers: Headers, redactHeadersWhen: CIString => Boolean): String = {
    val preparedHeaders = headers
      .redactSensitive(redactHeadersWhen)
      .headers
      .iterator
      .map { header =>
        s"""--header ${PosixQuoting.quote(s"${header.name}: ${header.value}")}"""
      }
      .mkString(newline)

    if (preparedHeaders.isEmpty) "" else newline + preparedHeaders
  }

  def requestToCurlWithoutBody[F[_]](
      request: Request[F],
      redactHeadersWhen: CIString => Boolean,
  ): String = {
    val params = List(
      prepareMethodName(request.method),
      prepareUri(request.uri),
      prepareHeaders(request.headers, redactHeadersWhen),
    ).mkString

    s"curl$params"
  }

  def requestToCurlWithBody[F[_]: Concurrent](
      request: Request[F],
      redactHeadersWhen: CIString => Boolean,
  )(implicit defaultCharset: Charset): F[(String, Request[F])] = {
    val isBinary = request.contentType.exists(_.mediaType.binary)
    val isJson = request.contentType.exists(mT =>
      mT.mediaType == MediaType.application.json || mT.mediaType.subType.endsWith("+json")
    )
    val makeCurl: String => String =
      bodyString => {
        val bodyArg =
          if (bodyString.isEmpty) "" else s"$newline--data ${PosixQuoting.quote(bodyString)}"
        s"${request.asCurl(redactHeadersWhen)}$bodyArg"
      }

    request.entity match {
      case Entity.Empty =>
        val curl = makeCurl("")
        Applicative[F].pure(curl -> request)

      case Entity.Strict(b) =>
        val bodyString =
          if (!isBinary || isJson) {
            val cs = request.charset.getOrElse(defaultCharset).nioCharset
            b.decodeStringLenient()(cs)
          } else
            b.toHex
        val curl = makeCurl(bodyString)
        Applicative[F].pure(curl -> request)

      case Entity.Streamed(body, _) =>
        for {
          cachedBodyStream <- body.compile[F, F, Byte].toVector.map(fs2.Stream.emits(_))
          bodyString <- {
            val cs = request.charset.getOrElse(defaultCharset).nioCharset
            if (!isBinary || isJson)
              cachedBodyStream.through(text.decodeWithCharset(cs)).compile[F, F, String].string
            else
              cachedBodyStream.compile[F, F, Byte].to(ByteVector).map(_.toHex)
          }
          curl = makeCurl(bodyString)
          refreshedRequest = request.withBodyStream(cachedBodyStream)
        } yield curl -> refreshedRequest
    }
  }
}
