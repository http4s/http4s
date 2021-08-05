/*
 * Copyright 2013 http4s.org
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
package websocket

import cats.effect.Async
import cats.syntax.all._
import org.http4s.internal.jsdeps.crypto.BufferSource
import org.http4s.internal.jsdeps.crypto.HashAlgorithm
import org.http4s.internal.jsdeps.webcrypto

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scala.scalajs.js.typedarray._

private[websocket] trait WebSocketHandshakePlatform { self: WebSocketHandshake.type =>
  private[websocket] def genAcceptKey[F[_]: Async](str: String): F[String] =
    Async[F]
      .fromPromise {
        Async[F].delay {
          webcrypto.subtle.digest(
            HashAlgorithm.`SHA-1`,
            (str + magicString).getBytes().toTypedArray.asInstanceOf[BufferSource])
        }
      }
      .map { case bytes: ArrayBuffer =>
        StandardCharsets.ISO_8859_1
          .decode(Base64.getEncoder.encode(TypedArrayBuffer.wrap(bytes)))
          .toString()
      }
}
