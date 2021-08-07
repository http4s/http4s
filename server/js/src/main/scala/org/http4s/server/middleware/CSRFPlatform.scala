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

package org.http4s.server
package middleware

import java.nio.charset.StandardCharsets
import org.http4s.internal.{decodeHexString, encodeHexString}
import org.http4s.js.webcrypto
import org.http4s.js.webcrypto.subtle
import cats.effect.Async
import scala.scalajs.js
import scala.scalajs.js.typedarray._
import cats.syntax.all._
import org.http4s.js.crypto.KeyUsage
import org.http4s.js.crypto.KeyFormat
import org.http4s.js.crypto.BufferSource
import org.http4s.js.crypto.KeyAlgorithm

private[middleware] trait CSRFPlatform[F[_], G[_]] { self: CSRF[F, G] =>

  import CSRF._

  private def signTokenNonce[M[_]](joined: String)(implicit F: Async[M]): M[Array[Byte]] =
    F.fromPromise {
      F.delay {
        subtle.sign(
          key.algorithm,
          key,
          joined.getBytes(StandardCharsets.UTF_8).toTypedArray.asInstanceOf[BufferSource])
      }
    }.map { case bytes: ArrayBuffer =>
      val bb = TypedArrayBuffer.wrap(bytes)
      val byteArray = new Array[Byte](bb.remaining())
      bb.get(byteArray)
      byteArray
    }

  /** Sign our token using the current time in milliseconds as a nonce
    * Signing and generating a token is potentially a unsafe operation
    * if constructed with a bad key.
    */
  def signToken[M[_]](rawToken: String)(implicit F: Async[M]): M[CSRFToken] = {
    val joined = rawToken + "-" + clock.millis().toString()
    signTokenNonce(joined).map(ba => lift(joined + "-" + encodeHexString(ba)))
  }

  /** Decode our CSRF token, check the signature
    * and extract the original token string to sign
    */
  def extractRaw[M[_]](rawToken: String)(implicit F: Async[M]): M[Either[CSRFCheckFailed, String]] =
    rawToken.split("-") match {
      case Array(raw, nonce, signed) =>
        signTokenNonce(raw + "-" + nonce).map { out =>
          decodeHexString(signed) match {
            case Some(decoded) =>
              if (digestIsEqual(out, decoded))
                Right(raw)
              else
                Left(CSRFCheckFailed)
            case None =>
              Left(CSRFCheckFailed)
          }
        }
      case _ =>
        F.pure(Left(CSRFCheckFailed))
    }

}

private[middleware] trait CSRFSingletonPlatform { self: CSRF.type =>
  type SecretKey = org.http4s.js.crypto.CryptoKey
  val SigningAlgo: KeyAlgorithm =
    js.Dynamic.literal(name = "HMAC", hash = "SHA-1").asInstanceOf[KeyAlgorithm]

  /** A Constant-time string equality */
  def isEqual(s1: String, s2: String): Boolean =
    digestIsEqual(s1.getBytes(StandardCharsets.UTF_8), s2.getBytes(StandardCharsets.UTF_8))

  /** Generate an unsigned CSRF token from a `SecureRandom` */
  private[middleware] def genTokenString: String = {
    val arrayBuffer = new Int8Array(CSRFTokenLength)
    webcrypto.getRandomValues(arrayBuffer.asInstanceOf[ArrayBufferView])
    val bytes = new Array[Byte](CSRFTokenLength)
    TypedArrayBuffer.wrap(arrayBuffer).get(bytes)
    encodeHexString(bytes)
  }

  /** Generate a signing Key for the CSRF token */
  def generateSigningKey[F[_]]()(implicit F: Async[F]): F[SecretKey] =
    F.fromPromise {
      F.delay {
        subtle.generateKey(SigningAlgo, false, js.Array(KeyUsage.sign))
      }
    }.map(_.asInstanceOf[SecretKey])

  /** Build a new HMACSHA1 Key for our CSRF Middleware
    * from key bytes. This operation is unsafe, in that
    * any amount less than 20 bytes will throw an exception when loaded
    * into `Mac`. Any keys larger than 64 bytes are just hashed.
    *
    * For more information, refer to: https://tools.ietf.org/html/rfc2104#section-3
    *
    * Use for loading a key from a config file, after having generated
    * one safely
    */
  def buildSigningKey[F[_]](array: Array[Byte])(implicit F: Async[F]): F[SecretKey] =
    F.fromPromise {
      F.delay {
        subtle.importKey(
          KeyFormat.raw,
          array.toTypedArray.asInstanceOf[BufferSource],
          SigningAlgo,
          false,
          js.Array(KeyUsage.sign)
        )
      }
    }.map(_.asInstanceOf[SecretKey])

  private[middleware] def digestIsEqual(digesta: Array[Byte], digestb: Array[Byte]) =
    if (digesta eq digestb) true
    else if (digesta == null || digestb == null) false
    else {
      val lenA = digesta.length;
      val lenB = digestb.length;

      if (lenB == 0) {
        lenA == 0;
      } else {
        var result = 0;
        result |= lenA - lenB;

        var i = 0
        // time-constant comparison
        while (i < lenA) {
          val indexB = ((i - lenB) >>> 31) * i;
          result |= digesta(i) ^ digestb(indexB);
          i += 1
        }
        result == 0

      }
    }

}
