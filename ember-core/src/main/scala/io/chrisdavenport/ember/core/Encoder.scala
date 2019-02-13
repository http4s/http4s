package org.http4s.ember.core

import cats.effect._
import fs2._
import org.http4s._
import cats._
import cats.implicits._
import Shared._

object Encoder {

  def respToBytes[F[_]: Sync](resp: Response[F]): Stream[F, Byte] = {
    val headerStrings: List[String] = resp.headers.map(h => h.name + ": " + h.value).toList

    val initSection = Stream(show"${resp.httpVersion} ${resp.status}") ++ Stream.emits(
      headerStrings)

    val body = Alternative[Option]
      .guard(resp.isChunked)
      .fold(resp.body)(_ => resp.body.through(ChunkedEncoding.encode[F]))

    initSection.covary[F].intersperse("\r\n").through(text.utf8Encode) ++
      Stream.chunk(Chunk.ByteVectorChunk(`\r\n\r\n`)) ++
      body
  }

  def reqToBytes[F[_]: Sync](req: Request[F]): Stream[F, Byte] = {
    // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
    val requestLine = show"${req.method} ${req.uri.renderString} ${req.httpVersion}"

    val finalHeaders = req.headers ++
      req.uri.authority.toSeq
        .flatMap(auth => Headers(Header("Host", auth.renderString)))

    val headerStrings: List[String] = finalHeaders.map(h => h.name + ": " + h.value).toList

    val initSection = Stream(requestLine) ++ Stream.emits(headerStrings)

    val body = Alternative[Option]
      .guard(req.isChunked)
      .fold(req.body)(_ => req.body.through(ChunkedEncoding.encode[F]))

    initSection.covary[F].intersperse("\r\n").through(text.utf8Encode) ++
      Stream.chunk(Chunk.ByteVectorChunk(`\r\n\r\n`)) ++
      body
  }
}
