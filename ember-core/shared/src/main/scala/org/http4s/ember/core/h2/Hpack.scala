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

package org.http4s.ember.core.h2

import cats.data._
import cats.effect._
import cats.syntax.all._
import org.http4s.ember.core.EmberException
import scodec.bits._

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** A simple interface for HPACK encoding and decoding. The implementation is
  * not thread-safe, so it should be used in a single-threaded context.
  */
private[h2] trait Hpack[F[_]] {
  def encodeHeaders(headers: NonEmptyList[(String, String, Boolean)]): F[ByteVector]
  def decodeHeaders(bv: ByteVector): F[List[(String, String)]]
  def discardHeaders(bv: ByteVector): F[Unit]
}

private[h2] object Hpack extends HpackPlatform {
  def create[F[_]: Async](maxHeaderListSize: Int): F[Hpack[F]] = for {
    e <- Sync[F].delay(new Encoder(4096))
    d <- Sync[F].delay(new Decoder(maxHeaderListSize, 4096))
  } yield new Impl(e, d, maxHeaderListSize.toLong)

  private class Impl[F[_]: Async](
      tEncoder: Encoder,
      tDecoder: Decoder,
      maxHeaderListSize: Long,
  ) extends Hpack[F] {
    def encodeHeaders(headers: NonEmptyList[(String, String, Boolean)]): F[ByteVector] =
      Hpack.encodeHeaders[F](tEncoder, headers.toList)
    def decodeHeaders(bv: ByteVector): F[List[(String, String)]] =
      Hpack.decodeHeaders[F](tDecoder, bv, maxHeaderListSize)
    def discardHeaders(bv: ByteVector): F[Unit] =
      Hpack.discardHeaders[F](tDecoder, bv, maxHeaderListSize)

  }

  def decodeHeaders[F[_]: Sync](
      tDecoder: Decoder,
      bv: ByteVector,
      maxHeaderListSize: Long,
  ): F[List[(String, String)]] = Sync[F].delay {
    val buffer = List.newBuilder[(String, String)]
    decode(tDecoder, bv, maxHeaderListSize) { (name, value) =>
      buffer.+=(
        new String(name, StandardCharsets.ISO_8859_1) -> new String(
          value,
          StandardCharsets.ISO_8859_1,
        )
      )
      ()
    }
    buffer.result()
  }

  def discardHeaders[F[_]: Sync](
      tDecoder: Decoder,
      bv: ByteVector,
      maxHeaderListSize: Long,
  ): F[Unit] = Sync[F].delay(decode(tDecoder, bv, maxHeaderListSize)((_, _) => ()))

  private def decode(
      tDecoder: Decoder,
      bv: ByteVector,
      maxHeaderListSize: Long,
  )(onHeader: (Array[Byte], Array[Byte]) => Unit): Unit = {
    var decodedSize = 0L
    val is = bv.toInputStream
    val listener = new HeaderListener {
      def addHeader(name: Array[Byte], value: Array[Byte], sensitive: Boolean): Unit = {
        // The HPACK decoder implementation does not track the 32 byte overhead, nor does it count
        // indexed references. So we need to do the accounting ourselves.
        decodedSize += name.length + value.length + 32 // + 32 per overhead in HTTP/2 spec
        if (decodedSize > maxHeaderListSize) {
          throw EmberException.MessageTooLong(maxHeaderListSize.toInt)
        }
        onHeader(name, value)
      }
    }

    tDecoder.decode(is, listener)
    val truncated = tDecoder.endHeaderBlock()

    if (truncated) {
      throw EmberException.MessageTooLong(maxHeaderListSize.toInt)
    }
  }

  def encodeHeaders[F[_]: Sync](
      tEncoder: Encoder,
      headers: List[(String, String, Boolean)],
  ): F[ByteVector] = Sync[F].delay {
    val os = new ByteVectorOutputStream(1024)
    headers.foreach { h =>
      tEncoder.encodeHeader(
        os,
        h._1.getBytes(StandardCharsets.ISO_8859_1),
        h._2.getBytes(StandardCharsets.ISO_8859_1),
        h._3,
      )
    }
    os.toByteVector()
  }

  private final class ByteVectorOutputStream(size: Int) extends ByteArrayOutputStream(size) {
    def toByteVector(): ByteVector = ByteVector.view(buf, 0, count)
  }

}
