package org.http4s.ember.core

import cats.effect._
import fs2._
import org.http4s._
import cats._
import cats.implicits._
import Shared._

private[ember] object Encoder {

  def respToBytes[F[_]: Sync](resp: Response[F]): Stream[F, Byte] = {
    val headerStrings : List[String] = resp.headers.toList.map(h => h.name.show + ": " + h.value).toList

    val initSection = Stream(show"${resp.httpVersion} ${resp.status}") ++ Stream.emits(headerStrings)

    val body = Alternative[Option].guard(resp.isChunked)
      .fold(resp.body)(_ => resp.body.through(ChunkedEncoding.encode[F]))

    initSection.covary[F].intersperse("\r\n").through(text.utf8Encode) ++ 
    Stream.chunk(Chunk.ByteVectorChunk(`\r\n\r\n`)) ++
    body
  }

  def reqToBytes[F[_]: Sync](req: Request[F]): Stream[F, Byte] = {
    // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
    val requestLine = show"${req.method} ${req.uri.renderString} ${req.httpVersion}"

    val finalHeaders =
      req.uri.authority.fold(Headers.of())(auth => Headers.of(Header("Host", auth.renderString))) ++ req.headers

    val headerStrings : List[String] = finalHeaders.toList.map(h => h.name.show + ": " + h.value).toList

    val initSection = Stream(requestLine) ++ Stream.emits(headerStrings)

    val body = Alternative[Option].guard(req.isChunked)
      .fold(req.body)(_ => req.body.through(ChunkedEncoding.encode[F]))

    initSection.covary[F].intersperse("\r\n").through(text.utf8Encode) ++
    Stream.chunk(Chunk.ByteVectorChunk(`\r\n\r\n`)) ++
    body
  }
}