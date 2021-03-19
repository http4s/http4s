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

import fs2._
import org.http4s._
import org.http4s.headers.{Host, `Content-Length`}
import java.nio.charset.StandardCharsets
import org.http4s.Entity.Strict
import org.http4s.Entity.TrustMe
import org.http4s.Entity.Chunked
import org.http4s.Entity.Empty

private[ember] object Encoder {

  private val SPACE = " "
  private val CRLF = "\r\n"
  val chunkedTansferEncodingHeaderRaw = "Transfer-Encoding: chunked"
  private val contentLengthPrefix = "Content-Length: "

  def respToBytes[F[_]](resp: Response[F], writeBufferSize: Int = 32 * 1024): Stream[F, Byte] = {
    val initSection = {
      val stringBuilder = new StringBuilder()

      // Response Prelude: HTTP-Version SP STATUS CRLF
      stringBuilder
        .append(resp.httpVersion.renderString)
        .append(SPACE)
        .append(resp.status.renderString)
        .append(CRLF)


      // Apply each header followed by a CRLF
      resp.headers.foreach { h =>
        if (h.name == `Content-Length`.name) ()
        else {
          stringBuilder
            .append(h.name)
            .append(": ")
            .append(h.value)
            .append(CRLF)
          ()
        }
      }
      if (resp.status.isEntityAllowed) {
        resp.entity match {
          case Strict(chunk) =>
            val length = `Content-Length`.unsafeFromLong(chunk.size.toLong)
            stringBuilder
              .append(contentLengthPrefix)
              .append(length.length)
              .append(CRLF)
          case TrustMe(_, size) =>
            val length = `Content-Length`.unsafeFromLong(size.toLong)
            stringBuilder
              .append(contentLengthPrefix)
              .append(length.length)
              .append(CRLF)
          case Chunked(_) =>
            stringBuilder
              .append(chunkedTansferEncodingHeaderRaw)
              .append(CRLF)

          case Empty() =>
            stringBuilder
              .append(`Content-Length`.zero)
              .append(CRLF)
        }
        ()
      }
      // Final CRLF terminates headers and signals body to follow.
      stringBuilder.append(CRLF)
      stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1)
    }

    resp.entity match {
      case Strict(chunk) =>
        (Stream.chunk(Chunk.array(initSection)) ++ Stream.chunk(chunk))
          .chunkMin(writeBufferSize)
          .flatMap(Stream.chunk)
      case TrustMe(body, _) =>
        (Stream.chunk(Chunk.array(initSection)) ++ body)
          .chunkMin(writeBufferSize)
          .flatMap(Stream.chunk)
      case Chunked(body) =>
        Stream.chunk(Chunk.array(initSection)) ++ body.through(ChunkedEncoding.encode[F])
      case Empty() => Stream.chunk(Chunk.array(initSection))
    }
  }

  private val NoPayloadMethods: Set[Method] =
    Set(Method.GET, Method.DELETE, Method.CONNECT, Method.TRACE)

  def reqToBytes[F[_]](req: Request[F], writeBufferSize: Int = 32 * 1024): Stream[F, Byte] = {
    val initSection = {
      val stringBuilder = new StringBuilder()

      // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
      stringBuilder
        .append(req.method.renderString)
        .append(SPACE)
        .append(req.uri.toOriginForm.renderString)
        .append(SPACE)
        .append(req.httpVersion.renderString)
        .append(CRLF)

      // Host From Uri Becomes Header if not already present in headers
      if (req.headers.get[Host].isEmpty)
        req.uri.authority.foreach { auth =>
          stringBuilder
            .append("Host: ")
            .append(auth.renderString)
            .append(CRLF)
        }

      // Apply each header followed by a CRLF
      req.headers.foreach { h =>
        if (h.name == `Content-Length`.name) ()
        else {
          stringBuilder
            .append(h.name)
            .append(": ")
            .append(h.value)
            .append(CRLF)
          ()
        }
      }

      if (!NoPayloadMethods.contains(req.method)) {
        req.entity match {
          case Strict(chunk) =>
            val length = `Content-Length`.unsafeFromLong(chunk.size.toLong)
            stringBuilder
              .append("Content")
              .append(length)
              .append(CRLF)
          case TrustMe(_, size) =>
            val length = `Content-Length`.unsafeFromLong(size.toLong)
            stringBuilder
              .append(contentLengthPrefix)
              .append(length.length)
              .append(CRLF)
          case Chunked(_) =>
            stringBuilder.append(chunkedTansferEncodingHeaderRaw).append(CRLF)
            ()
          case Empty() =>
            stringBuilder
              .append(contentLengthPrefix)
              .append(0)
              .append(CRLF)
            ()
        }

        ()
      }
      // Final CRLF terminates headers and signals body to follow.
      stringBuilder.append(CRLF)
      stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1)
    }
    req.entity match {
      case Strict(chunk) =>
        Stream.chunk(Chunk.concat(Chunk.array(initSection) :: chunk :: Nil))
      case TrustMe(body, _) =>
        (Stream.chunk(Chunk.array(initSection)) ++ body)
          .chunkMin(writeBufferSize)
          .flatMap(Stream.chunk)
      case Chunked(body) =>
        Stream.chunk(Chunk.array(initSection)) ++ body.through(ChunkedEncoding.encode[F])
      case Empty() =>
        Stream.chunk(Chunk.array(initSection))
    }
  }
}
