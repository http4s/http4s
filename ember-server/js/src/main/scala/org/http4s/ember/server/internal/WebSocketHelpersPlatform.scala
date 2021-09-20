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

package org.http4s.ember.server.internal

import cats.effect.Async
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.io.Duplex
import org.http4s.websocket.Rfc6455
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets
import scala.scalajs.js

private[internal] trait WebSocketHelpersPlatform {

  private val crypto = js.Dynamic.global.require("crypto")

  private[internal] def serverHandshake[F[_]](value: String)(implicit F: Async[F]): F[ByteVector] =
    F.delay(crypto.createHash("sha1").asInstanceOf[Duplex]).flatMap { hash =>
      fs2.io
        .readReadable[F](F.pure(hash))
        .chunkAll
        .concurrently(
          Stream
            .emit(value)
            .through(fs2.text.encode(StandardCharsets.US_ASCII))
            .++(Stream.chunk(Chunk.byteVector(ByteVector.view(Rfc6455.handshakeMagicBytes))))
            .through(fs2.io.writeWritable[F](F.pure(hash))))
        .compile
        .lastOrError
        .map(_.toByteVector)
    }

}
