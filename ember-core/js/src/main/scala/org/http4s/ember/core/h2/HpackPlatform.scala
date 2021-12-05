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

import cats.effect._
import cats.syntax.all._
import scodec.bits._
import cats.data._

import scala.scalajs.js.JSConverters._

private[h2] trait HpackPlatform {

  def create[F[_]](implicit F: Async[F]): F[Hpack[F]] = F.delay {
    val compressor = new facade.Compressor(facade.HpackOptions(4096))
    val decompressor = new facade.Decompressor(facade.HpackOptions(4096))
    new Hpack[F] {
      def encodeHeaders(headers: NonEmptyList[(String, String, Boolean)]): F[ByteVector] = {
        val jsHeaders = headers
          .map { case (name, value, huffman) =>
            facade.Header(name, value, huffman)
          }
          .toList
          .toJSArray
        F.delay(compressor.write(jsHeaders)) *> F.delay(ByteVector.view(compressor.read()))
      }

      def decodeHeaders(bv: ByteVector): F[NonEmptyList[(String, String)]] =
        F.delay(decompressor.write(bv.toUint8Array)) *>
          F.delay(decompressor.execute()) *>
          F.delay {
            val builder = List.newBuilder[(String, String)]
            while (
              Option(decompressor.read()).map { header =>
                builder += ((header.name, header.value))
              }.isDefined
            ) {}
            builder.result()
          }.flatMap(NonEmptyList.fromList(_).liftTo[F](new NoSuchElementException))
    }
  }

}
