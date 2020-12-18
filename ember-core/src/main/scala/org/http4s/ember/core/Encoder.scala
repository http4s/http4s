/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core

import cats.effect._
import fs2._
import org.http4s._
import org.http4s.headers.`Content-Length`
import java.nio.charset.StandardCharsets

private[ember] object Encoder {

  private val SPACE = " "
  private val CRLF = "\r\n"
  val chunkedTansferEncodingHeaderRaw = "Transfer-Encoding: chunked"

  def respToBytes[F[_]: Sync](
      resp: Response[F],
      writeBufferSize: Int = 32 * 1024): Stream[F, Byte] = {
    var chunked = resp.isChunked
    val initSection = {
      var appliedContentLength = false
      val stringBuilder = new StringBuilder()

      // Response Prelude: HTTP-Version SP STATUS CRLF
      stringBuilder
        .append(resp.httpVersion.renderString)
        .append(SPACE)
        .append(resp.status.renderString)
        .append(CRLF)

      // Apply each header followed by a CRLF
      resp.headers.foreach { h =>
        if (h.is(`Content-Length`)) appliedContentLength = true
        else ()

        stringBuilder
          .append(h.renderString)
          .append(CRLF)
        ()
      }
      if (!chunked && !appliedContentLength) {
        stringBuilder.append(chunkedTansferEncodingHeaderRaw).append(CRLF)
        chunked = true
        ()
      }
      // Final CRLF terminates headers and signals body to follow.
      stringBuilder.append(CRLF)
      stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1)
    }

    if (chunked)
      Stream.chunk(Chunk.array(initSection)) ++ resp.body.through(ChunkedEncoding.encode[F])
    else
      (Stream.chunk(Chunk.array(initSection)) ++ resp.body)
        .chunkMin(writeBufferSize)
        .flatMap(Stream.chunk)
  }

  def reqToBytes[F[_]: Sync](req: Request[F], writeBufferSize: Int = 32 * 1024): Stream[F, Byte] = {
    var chunked = req.isChunked
    val initSection = {
      var appliedContentLength = false
      val stringBuilder = new StringBuilder()

      // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
      stringBuilder
        .append(req.method.renderString)
        .append(SPACE)
        .append(req.uri.renderString)
        .append(SPACE)
        .append(req.httpVersion.renderString)
        .append(CRLF)

      // Host From Uri Becomes Header if not already present in headers
      if (org.http4s.headers.Host.from(req.headers).isEmpty)
        req.uri.authority.foreach { auth =>
          stringBuilder
            .append("Host: ")
            .append(auth.renderString)
            .append(CRLF)
        }

      // Apply each header followed by a CRLF
      req.headers.foreach { h =>
        if (h.is(`Content-Length`)) appliedContentLength = true
        else ()

        stringBuilder
          .append(h.renderString)
          .append(CRLF)
        ()
      }

      if (!chunked && !appliedContentLength) {
        stringBuilder.append(chunkedTansferEncodingHeaderRaw).append(CRLF)
        chunked = true
        ()
      }

      // Final CRLF terminates headers and signals body to follow.
      stringBuilder.append(CRLF)
      stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1)
    }
    if (chunked)
      Stream.chunk(Chunk.array(initSection)) ++ req.body.through(ChunkedEncoding.encode[F])
    else
      (Stream.chunk(Chunk.array(initSection)) ++ req.body)
        .chunkMin(writeBufferSize)
        .flatMap(Stream.chunk)
  }
}
