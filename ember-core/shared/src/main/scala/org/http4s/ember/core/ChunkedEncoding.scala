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

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/Spinoco/fs2-http/blob/c8b164b2b319903ca15e33a4f312cded63ea9882/src/main/scala/spinoco/fs2/http/internal/ChunkedEncoding.scala
 * Copyright (c) 2017 Spinoco
 * See licenses/LICENSE_fs2-http
 */

package org.http4s
package ember.core

import cats._
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.syntax.all._
import fs2._
import org.http4s.internal.appendSanitized
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal

import Shared._

private[ember] object ChunkedEncoding {

  /** From fs2-http
    * decodes from the HTTP chunked encoding. After last chunk this terminates. Allows to specify max header size, after which this terminates
    * Please see https://en.wikipedia.org/wiki/Chunked_transfer_encoding for details
    */
  def decode[F[_]](
      head: Array[Byte],
      read: F[Option[Chunk[Byte]]],
      maxHeaderSize: Int,
      maxChunkHeaderSize: Int,
      trailers: Deferred[F, Headers],
      rest: Ref[F, Option[Array[Byte]]],
  )(implicit F: MonadThrow[F]): Stream[F, Byte] = {
    // on left reading the header of chunk (acting as buffer)
    // on right reading the chunk itself, and storing remaining bytes of the chunk
    def go(expect: Either[ByteVector, Long], head: ByteVector): Pull[F, Byte, Unit] = {
      val nextBytes =
        if (head.nonEmpty)
          Pull.pure(Some(head))
        else
          Pull.eval(read).map(_.map(_.toByteVector))

      nextBytes.flatMap {
        case None =>
          Pull.raiseError(EmberException.ReachedEndOfStream())

        case Some(bv) =>
          expect match {
            case Left(header) =>
              val nh = header ++ bv
              val endOfHeader = nh.indexOfSlice(crlf)
              if (endOfHeader == 0)
                // strip any leading crlf on header, as this starts with /r/n
                go(expect, nh.drop(crlf.size))
              else if (endOfHeader < 0 && nh.size > maxChunkHeaderSize)
                Pull.raiseError[F](
                  EmberException.ChunkedEncodingError(
                    s"Failed to get Chunk header. Size exceeds max($maxChunkHeaderSize) : ${nh.size} ${nh.decodeUtf8}"
                  )
                )
              else if (endOfHeader < 0) go(Left(nh), ByteVector.empty)
              else {
                val (hdr, rem) = nh.splitAt(endOfHeader + crlf.size)
                readChunkedHeader(hdr.dropRight(crlf.size)) match {
                  case None =>
                    Pull.raiseError[F](
                      EmberException.ChunkedEncodingError(
                        s"Failed to parse chunked header : ${hdr.decodeUtf8}"
                      )
                    )
                  case Some(0) =>
                    // Done With Message, Now Parse Trailers
                    Pull.eval(
                      parseTrailers[F](maxHeaderSize)(rem.toArray, read)
                        .flatMap { t =>
                          trailers.complete(t.headers) >> rest.set(Some(t.rest))
                        }
                    ) >> Pull.done
                  case Some(sz) => go(Right(sz), rem)
                }
              }

            case Right(remains) =>
              if (remains > bv.size)
                Pull.output(Chunk.byteVector(bv)) >> go(Right(remains - bv.size), ByteVector.empty)
              else {
                val (out, next) = bv.splitAt(remains.toLong)
                Pull.output(Chunk.byteVector(out)) >> go(Left(ByteVector.empty), next)
              }
          }
      }
    }

    go(Left(ByteVector.empty), ByteVector.view(head)).stream
  }

  final case class Trailers(headers: Headers, rest: Array[Byte])

  private def parseTrailers[F[_]: MonadThrow](
      maxHeaderSize: Int
  )(buffer: Array[Byte], read: F[Option[Chunk[Byte]]]): F[Trailers] =
    if (buffer.length < 2) {
      read.flatMap {
        case None =>
          MonadThrow[F].raiseError(EmberException.ReachedEndOfStream())
        case Some(chunk) =>
          parseTrailers(maxHeaderSize)(Util.concatBytes(buffer, chunk), read)
      }
    } else if (buffer(0) == '\r' && buffer(1) == '\n') {
      Trailers(Headers.empty, buffer.drop(2)).pure[F]
    } else {
      Parser.MessageP
        .recurseFind(buffer, read, maxHeaderSize, Parser.HeaderP.ParserState.initial)(
          (state, buffer) => Parser.HeaderP.parse(buffer, maxHeaderSize, state)
        )(
          _.idx
        )
        .map { case (headerP, rest) => Trailers(headerP.headers, rest) }
    }

  private[this] val lastChunk: Stream[fs2.Pure, Byte] = {
    val bytes = Array[Byte]('0', '\r', '\n')
    Stream.chunk(Chunk.array(bytes))
  }

  private[this] val finalCrlf: Stream[fs2.Pure, Byte] = {
    val bytes = Array[Byte]('\r', '\n')
    Stream.chunk(Chunk.array(bytes))
  }

  /** Encodes chunk of bytes to http chunked encoding.
    */
  def encode[F[_]: Functor](trailers: F[Headers]): Pipe[F, Byte, Byte] = {
    def encodeChunk(bv: ByteVector): Chunk[Byte] =
      if (bv.isEmpty) Chunk.empty
      else
        Chunk.byteVector(
          ByteVector.view(bv.size.toHexString.toUpperCase.getBytes) ++ crlf ++ bv ++ crlf
        )
    _.mapChunks(ch => encodeChunk(ch.toByteVector)) ++
      lastChunk ++
      Stream.evalUnChunk(trailers.map(encodeTrailers(_))) ++
      finalCrlf
  }

  private def encodeTrailers(trailers: Headers): Chunk[Byte] = {
    val stringBuilder = new StringBuilder()

    trailers.foreach { h =>
      if (h.isNameValid) {
        stringBuilder
          .append(h.name)
          .append(": ")
        appendSanitized(stringBuilder, h.value)
        stringBuilder.append("\r\n")
      }
    }

    Chunk.array(stringBuilder.toString.getBytes(StandardCharsets.ISO_8859_1))
  }

  /** yields to size of header in case the chunked header was succesfully parsed, else yields to None */
  private def readChunkedHeader(hdr: ByteVector): Option[Long] =
    hdr.decodeUtf8.toOption.flatMap { s =>
      val parts = s.split(';') // lets ignore any extensions
      if (parts.isEmpty) None
      else
        try Some(java.lang.Long.parseLong(parts(0).trim, 16))
        catch { case NonFatal(_) => None }
    }
}
