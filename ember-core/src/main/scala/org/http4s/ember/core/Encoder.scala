package org.http4s.ember.core

import cats.effect._
import fs2._
import org.http4s._
import org.http4s.implicits._
import cats.implicits._
import Shared._

private[ember] object Encoder {
  def respToBytes[F[_]: Sync](resp: Response[F]): Stream[F, Byte] = {
    val headerStrings: List[String] =
      resp.headers.toList.map(h => h.name.show + ": " + h.value).toList

    val initSection = Stream(show"${resp.httpVersion.renderString} ${resp.status.renderString}") ++
      Stream.emits(headerStrings)

    val body = if (resp.isChunked) resp.body.through(ChunkedEncoding.encode[F]) else resp.body

    initSection.covary[F].intersperse("\r\n").through(text.utf8Encode) ++
      Stream.chunk(Chunk.ByteVectorChunk(`\r\n\r\n`)) ++
      body
  }

  def reqToBytes[F[_]: Sync](req: Request[F]): Stream[F, Byte] = {
    // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
    val requestLine =
      show"${req.method.renderString} ${req.uri.renderString} ${req.httpVersion.renderString}"

    val finalHeaders =
      req.uri.authority
        .fold(Headers.of())(auth => Headers.of(Header("Host", auth.renderString))) ++ req.headers

    val headerStrings: List[String] =
      finalHeaders.toList.map(h => h.name.show + ": " + h.value).toList

    val initSection = Stream(requestLine) ++ Stream.emits(headerStrings)

    val body = if (req.isChunked) req.body.through(ChunkedEncoding.encode[F]) else req.body

    initSection.covary[F].intersperse("\r\n").through(text.utf8Encode) ++
      Stream.chunk(Chunk.ByteVectorChunk(`\r\n\r\n`)) ++
      body
  }
}
