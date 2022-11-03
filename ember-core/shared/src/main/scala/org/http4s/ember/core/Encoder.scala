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

import cats.ApplicativeThrow
import fs2._
import org.http4s._
import org.http4s.headers.Host
import org.http4s.headers.`Content-Length`
import org.http4s.internal.CharPredicate
import org.http4s.internal.appendSanitized

import java.nio.charset.StandardCharsets

private[ember] object Encoder {

  private val SPACE = " "
  private val CRLF = "\r\n"
  val chunkedTransferEncodingHeaderRaw = "Transfer-Encoding: chunked"
  val zeroContentLengthRaw = "Content-Length: 0"

  def initSection[F[_]](resp: Response[F]): (Array[Byte], Boolean) = {
    var chunked = resp.isChunked
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
      if (h.isNameValid) {
        stringBuilder
          .append(h.name)
          .append(": ")
        appendSanitized(stringBuilder, h.value)
        stringBuilder.append(CRLF)
        if (h.name == `Content-Length`.name) {
          appliedContentLength = true
        }
        ()
      }
    }
    if (!appliedContentLength && resp.entity == Entity.Empty && resp.status.isEntityAllowed) {
      stringBuilder.append(zeroContentLengthRaw).append(CRLF)
      chunked = false
      ()
    } else if (!chunked && !appliedContentLength && resp.status.isEntityAllowed) {
      stringBuilder.append(chunkedTransferEncodingHeaderRaw).append(CRLF)
      chunked = true
      ()
    }
    // Final CRLF terminates headers and signals body to follow.
    stringBuilder.append(CRLF)
    (stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1), chunked)
  }

  def respToBytes[F[_]](
      resp: Response[F],
      writeBufferSize: Int = 32 * 1024,
  ): Stream[F, Byte] = {
    // resp.status.isEntityAllowed TODO
    val (initSectionBytes, chunked) = initSection(resp)
    val initSectionChunk = Chunk.array(initSectionBytes)

    if (chunked)
      Stream.chunk(initSectionChunk) ++ resp.body.through(ChunkedEncoding.encode[F])
    else
      (Stream.chunk(initSectionChunk) ++ resp.body)
        .chunkMin(writeBufferSize)
        .flatMap(Stream.chunk)
  }

  private val NoPayloadMethods: Set[Method] =
    Set(Method.GET, Method.DELETE, Method.CONNECT, Method.TRACE)

  def reqToBytes[F[_]: ApplicativeThrow](
      req: Request[F],
      writeBufferSize: Int = 32 * 1024,
  ): Stream[F, Byte] = {
    val uriOriginFormString = req.uri.toOriginForm.renderString

    if (uriOriginFormString.exists(ForbiddenUriCharacters)) {
      Stream.raiseError(new IllegalArgumentException(s"Invalid URI: ${uriOriginFormString}"))
    } else {
      var chunked = req.isChunked
      val initSection = {
        var appliedContentLength = false
        val stringBuilder = new StringBuilder()

        // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
        stringBuilder
          .append(req.method.renderString)
          .append(SPACE)
          .append(uriOriginFormString)
          .append(SPACE)
          .append(req.httpVersion.renderString)
          .append(CRLF)

        // Host From Uri Becomes Header if not already present in headers
        if (!req.headers.contains[Host])
          req.uri.authority.foreach { auth =>
            stringBuilder
              .append("Host: ")
              .append(auth.renderString)
              .append(CRLF)
          }

        // Apply each header followed by a CRLF
        req.headers.foreach { h =>
          if (h.isNameValid) {
            stringBuilder
              .append(h.name)
              .append(": ")
            appendSanitized(stringBuilder, h.value)
            stringBuilder.append(CRLF)
            if (h.name == `Content-Length`.name) {
              appliedContentLength = true
            }
            ()
          }
        }

        if (
          !appliedContentLength && req.body == EmptyBody && !NoPayloadMethods.contains(req.method)
        ) {
          stringBuilder.append(zeroContentLengthRaw).append(CRLF)
          chunked = false
        } else if (!chunked && !appliedContentLength && !NoPayloadMethods.contains(req.method)) {
          stringBuilder.append(chunkedTransferEncodingHeaderRaw).append(CRLF)
          chunked = true
          ()
        }

        // Final CRLF terminates headers and signals body to follow.
        stringBuilder.append(CRLF)
        stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1)
      }
      val initSectionChunk = Chunk.array(initSection)
      if (chunked)
        Stream.chunk(initSectionChunk) ++ req.body.through(ChunkedEncoding.encode[F])
      else {
        req.entity match {
          case Entity.Default(body, _) =>
            (Stream.chunk(initSectionChunk) ++ body)
              .chunkMin(writeBufferSize)
              .flatMap(Stream.chunk)
          case Entity.Strict(bytes) =>
            Stream.chunk(initSectionChunk ++ Chunk.byteVector(bytes))
          case Entity.Empty =>
            Stream.chunk(initSectionChunk)
        }
      }
    }
  }

  private val ForbiddenUriCharacters = CharPredicate(0x0.toChar, '\r', '\n')
}
