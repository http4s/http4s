/*
 * Copyright 2014 http4s.org
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

package org.http4s
package server
package middleware

import cats.Functor
import cats.data.Kleisli
import cats.effect.{Async, Ref}
import cats.syntax.all._
import fs2.{Chunk, Stream}
import fs2.compression._
import java.util.zip.Deflater
import org.http4s.headers._
import org.log4s.getLogger
import scodec.bits.{BitVector, ByteOrdering, ByteVector}
import scodec.bits

object GZip {
  private[this] val logger = getLogger

  // TODO: It could be possible to look for F.pure type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply[F[_]: Functor, G[_]: Async](
      http: Http[F, G],
      bufferSize: Int = 32 * 1024,
      level: DeflateParams.Level = DeflateParams.Level.DEFAULT,
      isZippable: Response[G] => Boolean = defaultIsZippable[G](_: Response[G])
  ): Http[F, G] =
    Kleisli { (req: Request[G]) =>
      req.headers.get[`Accept-Encoding`] match {
        case Some(acceptEncoding) if satisfiedByGzip(acceptEncoding) =>
          http(req).map(zipOrPass(_, bufferSize, level, isZippable))
        case _ => http(req)
      }
    }

  def defaultIsZippable[F[_]](resp: Response[F]): Boolean = {
    val contentType = resp.headers.get[`Content-Type`]
    resp.headers.get[`Content-Encoding`].isEmpty &&
    resp.status.isEntityAllowed &&
    (contentType.isEmpty || contentType.get.mediaType.compressible ||
      (contentType.get.mediaType eq MediaType.application.`octet-stream`))
  }

  private def satisfiedByGzip(acceptEncoding: `Accept-Encoding`) =
    acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
      ContentCoding.`x-gzip`)

  private def zipOrPass[F[_]: Async](
      response: Response[F],
      bufferSize: Int,
      level: DeflateParams.Level,
      isZippable: Response[F] => Boolean): Response[F] =
    response match {
      case resp if isZippable(resp) => zipResponse(bufferSize, level, resp)
      case resp => resp // Don't touch it, Content-Encoding already set
    }

  private def zipResponse[F[_]: Async](
      bufferSize: Int,
      level: DeflateParams.Level,
      resp: Response[F]): Response[F] = {

    logger.trace("GZip middleware encoding content")
    // Need to add the Gzip header and trailer

    val b = Stream.chunk(header) ++ Stream.eval(Ref.of(TrailerGen())).flatMap { ref =>
      resp.body
        .chunkLimit(bufferSize)
        .noneTerminate
        .evalMapAccumulate(TrailerGen()) {
          case (TrailerGen(crc, inputLength), Some(chunk)) =>
            val gen = TrailerGen(crc.updated(chunk.toBitVector), inputLength + chunk.size)
            (gen, chunk).pure
          case (gen, None) =>
            ref.set(gen).as((gen, Chunk.empty[Byte]))
        }
        .flatMap(x => Stream.chunk(x._2))
        .through(
          Compression[F].deflate(
            DeflateParams(
              level = level,
              header = ZLibParams.Header.GZIP,
              bufferSize = bufferSize))) ++
        Stream.eval(ref.get).flatMap { case TrailerGen(crc, inputLength) =>
          val checksum = crc.result.toByteVector.reverse
          val length = ByteVector.fromInt(
            (inputLength % GZIP_LENGTH_MOD).toInt,
            ordering = ByteOrdering.LittleEndian
          )
          Stream.chunk(Chunk.byteVector(checksum ++ length))
        }
    }
    resp
      .removeHeader[`Content-Length`]
      .putHeaders(`Content-Encoding`(ContentCoding.gzip))
      .copy(body = b)
  }

  private val GZIP_MAGIC_NUMBER = 0x8b1f
  private val GZIP_LENGTH_MOD = Math.pow(2, 32).toLong

  private val header: Chunk[Byte] = Chunk.array(
    Array(
      GZIP_MAGIC_NUMBER.toByte, // Magic number (int16)
      (GZIP_MAGIC_NUMBER >> 8).toByte, // Magic number  c
      Deflater.DEFLATED.toByte, // Compression method
      0.toByte, // Flags
      0.toByte, // Modification time (int32)
      0.toByte, // Modification time  c
      0.toByte, // Modification time  c
      0.toByte, // Modification time  c
      0.toByte, // Extra flags
      0.toByte
    ) // Operating system
  )

  private final case class TrailerGen(
      val crc: bits.crc.CrcBuilder[BitVector] = bits.crc.crc32Builder,
      val inputLength: Int = 0)

}
