/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import cats.effect._
import fs2._
import org.http4s._
// import cats.implicits._
// import Shared._
import java.nio.charset.StandardCharsets

private[ember] object Encoder {

  private val SPACE = " "
  private val CRLF = "\r\n"
  def respToBytes[F[_]: Sync](resp: Response[F]): Stream[F, Byte] = {
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
        stringBuilder
          .append(h.renderString)
          .append(CRLF)
        ()
      }
      // Final CRLF terminates headers and signals body to follow.
      stringBuilder.append(CRLF)
      stringBuilder.toString.getBytes(StandardCharsets.US_ASCII)
    }
    val body = if (resp.isChunked) resp.body.through(ChunkedEncoding.encode[F]) else resp.body

    Stream.chunk(Chunk.array(initSection)) ++
      body
  }

  def reqToBytes[F[_]: Sync](req: Request[F]): Stream[F, Byte] = {
    val initSection = {
      val stringBuilder = new StringBuilder()

      // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
      stringBuilder
        .append(req.method.renderString)
        .append(SPACE)
        .append(req.uri.renderString)
        .append(SPACE)
        .append(req.httpVersion.renderString)
        .append(CRLF)

      // Host From Uri Becomes Header
      req.uri.authority.foreach { auth =>
        stringBuilder
          .append("Host: ")
          .append(auth.renderString)
          .append(CRLF)
      }

      // Apply each header followed by a CRLF
      req.headers.foreach { h =>
        stringBuilder
          .append(h.renderString)
          .append(CRLF)
        ()
      }
      // Final CRLF terminates headers and signals body to follow.
      stringBuilder.append(CRLF)
      stringBuilder.toString.getBytes(StandardCharsets.US_ASCII)
    }
    val body = if (req.isChunked) req.body.through(ChunkedEncoding.encode[F]) else req.body

    Stream.chunk(Chunk.array(initSection)) ++
      body
  }
}
