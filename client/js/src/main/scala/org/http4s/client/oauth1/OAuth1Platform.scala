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
package client
package oauth1

import cats.effect.Async
import cats.syntax.all._
import org.http4s.js.webcrypto.subtle
import scala.scalajs.js
import scala.scalajs.js.typedarray._
import scala.scalajs.js.typedarray.TypedArrayBuffer
import org.http4s.js.crypto.KeyFormat
import org.http4s.js.crypto.KeyUsage
import org.http4s.js.crypto.KeyAlgorithm
import java.nio.charset.StandardCharsets
import org.http4s.js.crypto.CryptoKey
import java.util.Base64
import org.http4s.js.crypto.BufferSource

private[oauth1] trait OAuth1Platform {

  private[oauth1] def makeSHASig[F[_]](
      baseString: String,
      consumerSecret: String,
      tokenSecret: Option[String],
      algorithm: SignatureAlgorithm)(implicit F: Async[F]): F[String] =
    for {
      key <- Async[F]
        .fromPromise {
          val key = encode(consumerSecret) + "&" + tokenSecret.map(t => encode(t)).getOrElse("")
          F.fromEither(keyAlgorithmFor(algorithm)).flatMap { keyAlgorithm =>
            Async[F].delay {
              subtle.importKey(
                KeyFormat.raw,
                bytes(key).toTypedArray.asInstanceOf[BufferSource],
                keyAlgorithm,
                false,
                js.Array(KeyUsage.sign)
              )
            }
          }
        }
        .map(_.asInstanceOf[CryptoKey])
      sig <- Async[F]
        .fromPromise {
          Async[F].delay {
            subtle.sign(
              key.algorithm,
              key,
              bytes(baseString).toTypedArray.asInstanceOf[BufferSource])
          }
        }
        .map(_.asInstanceOf[ArrayBuffer])
    } yield StandardCharsets.ISO_8859_1
      .decode(Base64.getEncoder().encode(TypedArrayBuffer.wrap(sig)))
      .toString()

  private def bytes(str: String) = str.getBytes(StandardCharsets.UTF_8)

  private def keyAlgorithmFor(
      alg: SignatureAlgorithm): Either[IllegalArgumentException, KeyAlgorithm] =
    (alg match {
      case HmacSha1 => Right(("HMAC", "SHA-1"))
      case HmacSha256 => Right(("HMAC", "SHA-256"))
      case HmacSha512 => Right(("HMAC", "SHA-512"))
      case _ => Left(new IllegalArgumentException("Unsupported signature algorithm: " + alg))
    }).map { case (name, hash) =>
      js.Dynamic.literal(name = "HMAC", hash = "SHA-1").asInstanceOf[KeyAlgorithm]
    }
}
