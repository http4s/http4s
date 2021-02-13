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
import cats.syntax.all._
import cats.effect.concurrent.{Deferred, Ref}
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
  def decode[F[_]](
      head: Array[Byte],
      read: F[Option[Chunk[Byte]]],
      maxChunkHeaderSize: Int,
      trailers: Deferred[F, Headers],
      rest: Ref[F, Option[Array[Byte]]])(implicit F: MonadThrow[F]): Stream[F, Byte] = {
    // on left reading the header of chunk (acting as buffer)
    // on right reading the chunk itself, and storing remaining bytes of the chunk
    def go(expect: Either[ByteVector, Long], head: Array[Byte]): Pull[F, Byte, Unit] = {
      val uncons = if (head.nonEmpty) Pull.pure(Some(Chunk.bytes(head))) else Pull.eval(read)
      uncons.flatMap {
        case None => Pull.done
        case Some(h) =>
          val bv = h.toByteVector
          expect match {
            case Left(header) =>
              val nh = header ++ bv
              val endOfheader = nh.indexOfSlice(`\r\n`)
              if (endOfheader == 0)
                go(
                  expect,
                  bv.drop(`\r\n`.size).toArray
                ) //strip any leading crlf on header, as this starts with /r/n
              else if (endOfheader < 0 && nh.size > maxChunkHeaderSize)
                Pull.raiseError[F](EmberException.ChunkedEncodingError(
                  s"Failed to get Chunk header. Size exceeds max($maxChunkHeaderSize) : ${nh.size} ${nh.decodeUtf8}"))
              else if (endOfheader < 0) go(Left(nh), Array.emptyByteArray)
              else {
                val (hdr, rem) = nh.splitAt(endOfheader + `\r\n`.size)
                readChunkedHeader(hdr.dropRight(`\r\n`.size)) match {
                  case None =>
                    Pull.raiseError[F](
                      EmberException.ChunkedEncodingError(
                        s"Failed to parse chunked header : ${hdr.decodeUtf8}"))
                  case Some(0) =>
                    // Done With Message, Now Parse Trailers
                    Pull.eval(
                      parseTrailers[F](maxChunkHeaderSize)(rem.toArray, read)
                        .flatMap { case (hdrs, bytes) =>
                          trailers.complete(hdrs) >> rest.set(Some(bytes))
                        }
                    ) >> Pull.done
                  case Some(sz) => go(Right(sz), rem.toArray)
                }
              }

            case Right(remains) =>
              if (remains == bv.size)
                Pull.output(Chunk.ByteVectorChunk(bv)) >> go(
                  Left(ByteVector.empty),
                  Array.emptyByteArray)
              else if (remains > bv.size)
                Pull.output(Chunk.ByteVectorChunk(bv)) >> go(
                  Right(remains - bv.size),
                  Array.emptyByteArray)
              else {
                val (out, next) = bv.splitAt(remains.toLong)
                Pull.output(Chunk.ByteVectorChunk(out)) >> go(Left(ByteVector.empty), next.toArray)
              }
          }
      }
    }

    go(Left(ByteVector.empty), head).stream
  }

  private def parseTrailers[F[_]: MonadThrow](
      maxHeaderSize: Int
  )(head: Array[Byte], read: F[Option[Chunk[Byte]]]): F[(Headers, Array[Byte])] = {
    val uncons = if (head.nonEmpty) (Some(Chunk.bytes(head)): Option[Chunk[Byte]]).pure[F] else read
    uncons.flatMap {
      case None => (Headers.empty, Array.emptyByteArray).pure[F]
      case Some(chunk) =>
        if (chunk.isEmpty) parseTrailers(maxHeaderSize)(Array.emptyByteArray, read)
        else if (chunk.toByteVector.startsWith(Shared.`\r\n`))
          (Headers.empty, chunk.toArray.drop(`\r\n`.size.toInt)).pure[F]
        else
          Parser.HeaderP.parseHeaders(chunk.toArray, read, maxHeaderSize, None).map {
            case (headers, _, _, bytes) =>
              (headers, bytes)
          }
    }
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
