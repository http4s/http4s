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
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.syntax.all._
import fs2._
import scodec.bits.ByteVector

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
    def go(expect: Either[ByteVector, Long], head: Array[Byte]): Pull[F, Byte, Unit] = {
      val nextChunk = if (head.nonEmpty) Pull.pure(Some(Chunk.bytes(head))) else Pull.eval(read)
      nextChunk.flatMap {
        case None =>
          Pull.raiseError(EmberException.ReachedEndOfStream())
        case Some(h) =>
          val bv = h.toByteVector
          expect match {
            case Left(header) =>
              val nh = header ++ bv
              val endOfHeader = nh.indexOfSlice(`\r\n`)
              if (endOfHeader == 0)
                go(
                  expect,
                  nh.drop(`\r\n`.size).toArray,
                ) // strip any leading crlf on header, as this starts with /r/n
              else if (endOfHeader < 0 && nh.size > maxChunkHeaderSize)
                Pull.raiseError[F](
                  EmberException.ChunkedEncodingError(
                    s"Failed to get Chunk header. Size exceeds max($maxChunkHeaderSize) : ${nh.size} ${nh.decodeUtf8}"
                  )
                )
              else if (endOfHeader < 0) go(Left(nh), Array.emptyByteArray)
              else {
                val (hdr, rem) = nh.splitAt(endOfHeader + `\r\n`.size)
                readChunkedHeader(hdr.dropRight(`\r\n`.size)) match {
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
                  case Some(sz) => go(Right(sz), rem.toArray)
                }
              }

            case Right(remains) =>
              if (remains == bv.size)
                Pull.output(Chunk.ByteVectorChunk(bv)) >> go(
                  Left(ByteVector.empty),
                  Array.emptyByteArray,
                )
              else if (remains > bv.size)
                Pull.output(Chunk.ByteVectorChunk(bv)) >> go(
                  Right(remains - bv.size),
                  Array.emptyByteArray,
                )
              else {
                val (out, next) = bv.splitAt(remains.toLong)
                Pull.output(Chunk.ByteVectorChunk(out)) >> go(Left(ByteVector.empty), next.toArray)
              }
          }
      }
    }

    go(Left(ByteVector.empty), head).stream
  }

  final case class Trailers(headers: Headers, rest: Array[Byte])

  private def parseTrailers[F[_]: MonadThrow](
      maxHeaderSize: Int
  )(buffer: Array[Byte], read: F[Option[Chunk[Byte]]]): F[Trailers] =
    if (buffer.startsWith(Shared.`\r\n`.toArray)) {
      Trailers(Headers.empty, buffer.drop(`\r\n`.size.toInt)).pure[F]
    } else if (buffer.length < 2) {
      read.flatMap {
        case None =>
          MonadThrow[F].raiseError(EmberException.ReachedEndOfStream())
        case Some(chunk) =>
          parseTrailers(maxHeaderSize)(buffer ++ chunk.toArray[Byte], read)
      }
    } else {
      Parser.MessageP
        .recurseFind(buffer, read, maxHeaderSize)(buffer =>
          Parser.HeaderP.parseHeaders(buffer, 0, maxHeaderSize)
        )(_.idx)
        .map { case (headerP, rest) => Trailers(headerP.headers, rest) }
    }

  private val lastChunk: Chunk[Byte] =
    Chunk.ByteVectorChunk((ByteVector('0') ++ `\r\n` ++ `\r\n`).compact)

  /** Encodes chunk of bytes to http chunked encoding.
    */
  def encode[F[_]]: Pipe[F, Byte, Byte] = {
    def encodeChunk(bv: ByteVector): Chunk[Byte] =
      if (bv.isEmpty) Chunk.empty
      else
        Chunk.ByteVectorChunk(
          ByteVector.view(bv.size.toHexString.toUpperCase.getBytes) ++ `\r\n` ++ bv ++ `\r\n`
        )
    _.mapChunks { ch =>
      encodeChunk(ch.toByteVector)
    } ++ Stream.chunk(lastChunk)
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
