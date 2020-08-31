/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/Spinoco/fs2-http/blob/c8b164b2b319903ca15e33a4f312cded63ea9882/src/main/scala/spinoco/fs2/http/internal/ChunkedEncoding.scala
 * Copyright (c) 2017 Spinoco
 * See licenses/LICENSE_fs2-http
 */

package org.http4s.ember.core

import cats._
import cats.implicits._
import cats.effect.concurrent.Deferred
import fs2._
import scodec.bits.ByteVector
import Shared._
import org.http4s.Headers

import scala.util.control.NonFatal

private[ember] object ChunkedEncoding {

  /** From fs2-http
    * decodes from the HTTP chunked encoding. After last chunk this terminates. Allows to specify max header size, after which this terminates
    * Please see https://en.wikipedia.org/wiki/Chunked_transfer_encoding for details
    */
  def decode[F[_]](maxChunkHeaderSize: Int, trailers: Deferred[F, Headers])(implicit
      F: MonadError[F, Throwable]): Pipe[F, Byte, Byte] = {
    // on left reading the header of chunk (acting as buffer)
    // on right reading the chunk itself, and storing remaining bytes of the chunk
    def go(expect: Either[ByteVector, Long], in: Stream[F, Byte]): Pull[F, Byte, Unit] =
      in.pull.uncons.flatMap {
        case None => Pull.done
        case Some((h, tl)) =>
          val bv = h.toByteVector
          expect match {
            case Left(header) =>
              val nh = header ++ bv
              val endOfheader = nh.indexOfSlice(`\r\n`)
              if (endOfheader == 0)
                go(
                  expect,
                  Stream.chunk(Chunk.ByteVectorChunk(bv.drop(`\r\n`.size))) ++ tl
                ) //strip any leading crlf on header, as this starts with /r/n
              else if (endOfheader < 0 && nh.size > maxChunkHeaderSize)
                Pull.raiseError[F](EmberException.ChunkedEncodingError(
                  s"Failed to get Chunk header. Size exceeds max($maxChunkHeaderSize) : ${nh.size} ${nh.decodeUtf8}"))
              else if (endOfheader < 0) go(Left(nh), tl)
              else {
                val (hdr, rem) = nh.splitAt(endOfheader + `\r\n`.size)
                readChunkedHeader(hdr.dropRight(`\r\n`.size)) match {
                  case None =>
                    Pull.raiseError[F](
                      EmberException.ChunkedEncodingError(
                        s"Failed to parse chunked header : ${hdr.decodeUtf8}"))
                  case Some(0) =>
                    // Done With Message, Now Parse Trailers
                    parseTrailers[F](maxChunkHeaderSize)(Stream.chunk(Chunk.byteVector(rem)) ++ tl)
                      .flatMap { hdrs =>
                        Pull.eval(trailers.complete(hdrs)) >> Pull.done
                      }
                  case Some(sz) => go(Right(sz), Stream.chunk(Chunk.ByteVectorChunk(rem)) ++ tl)
                }
              }

            case Right(remains) =>
              if (remains == bv.size)
                Pull.output(Chunk.ByteVectorChunk(bv)) >> go(Left(ByteVector.empty), tl)
              else if (remains > bv.size)
                Pull.output(Chunk.ByteVectorChunk(bv)) >> go(Right(remains - bv.size), tl)
              else {
                val (out, next) = bv.splitAt(remains.toLong)
                Pull.output(Chunk.ByteVectorChunk(out)) >> go(
                  Left(ByteVector.empty),
                  Stream.chunk(Chunk.ByteVectorChunk(next)) ++ tl)
              }
          }
      }

    go(Left(ByteVector.empty), _).stream
  }

  private def parseTrailers[F[_]: MonadError[*[_], Throwable]](
      maxHeaderSize: Int
  )(s: Stream[F, Byte]): Pull[F, Nothing, Headers] =
    s.pull.uncons.flatMap {
      case None => Pull.pure(Headers.empty)
      case Some((chunk, tl)) =>
        if (chunk.isEmpty) parseTrailers(maxHeaderSize)(tl)
        else if (chunk.toByteVector.startsWith(Shared.`\r\n`))
          Pull.pure(Headers.empty)
        else
          Parser.HeaderP.parseHeaders(Stream.chunk(chunk) ++ tl, maxHeaderSize, None).map {
            case (headers, _, _, _) =>
              headers
          }
    }

  private val lastChunk: Chunk[Byte] =
    Chunk.ByteVectorChunk((ByteVector('0') ++ `\r\n` ++ `\r\n`).compact)

  /**
    * Encodes chunk of bytes to http chunked encoding.
    */
  def encode[F[_]]: Pipe[F, Byte, Byte] = {
    def encodeChunk(bv: ByteVector): Chunk[Byte] =
      if (bv.isEmpty) Chunk.empty
      else
        Chunk.ByteVectorChunk(
          ByteVector.view(bv.size.toHexString.toUpperCase.getBytes) ++ `\r\n` ++ bv ++ `\r\n`)
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
