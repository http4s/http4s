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

import javax.crypto.Mac
import java.nio.charset.StandardCharsets
import org.http4s.internal.{decodeHexString, encodeHexString}
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import cats.effect.Async

private[middleware] trait CSRFPlatform[F[_], G[_]] { self: CSRF[F, G] =>

  import CSRF._

  /** Sign our token using the current time in milliseconds as a nonce
    * Signing and generating a token is potentially a unsafe operation
    * if constructed with a bad key.
    */
  def signToken[M[_]](rawToken: String)(implicit F: Async[M]): M[CSRFToken] =
    F.delay {
      val joined = rawToken + "-" + clock.millis()
      val mac = Mac.getInstance(CSRF.SigningAlgo)
      mac.init(key)
      val out = mac.doFinal(joined.getBytes(StandardCharsets.UTF_8))
      lift(joined + "-" + encodeHexString(out))
    }

  /** Decode our CSRF token, check the signature
    * and extract the original token string to sign
    */
  def extractRaw[M[_]](rawToken: String)(implicit F: Async[M]): M[Either[CSRFCheckFailed, String]] =
    F.pure {
      rawToken.split("-") match {
        case Array(raw, nonce, signed) =>
          val mac = Mac.getInstance(CSRF.SigningAlgo)
          mac.init(key)
          val out = mac.doFinal((raw + "-" + nonce).getBytes(StandardCharsets.UTF_8))
          decodeHexString(signed) match {
            case Some(decoded) =>
              if (MessageDigest.isEqual(out, decoded))
                Right(raw)
              else
                Left(CSRFCheckFailed)
            case None =>
              Left(CSRFCheckFailed)
          }
        case _ =>
          Left(CSRFCheckFailed)
      }
    }

}

private[middleware] trait CSRFSingletonPlatform { self: CSRF.type =>
  type SecretKey = javax.crypto.SecretKey
  val SigningAlgo: String = "HmacSHA1"

  private val CachedRandom: SecureRandom = {
    val r = new SecureRandom()
    r.nextBytes(new Array[Byte](InitialSeedArraySize))
    r
  }

  /** A Constant-time string equality */
  def isEqual(s1: String, s2: String): Boolean =
    MessageDigest.isEqual(s1.getBytes(StandardCharsets.UTF_8), s2.getBytes(StandardCharsets.UTF_8))

  /** Generate an unsigned CSRF token from a `SecureRandom` */
  private[middleware] def genTokenString: String = {
    val bytes = new Array[Byte](CSRFTokenLength)
    CachedRandom.nextBytes(bytes)
    encodeHexString(bytes)
  }

  /** Generate a signing Key for the CSRF token */
  def generateSigningKey[F[_]]()(implicit F: Async[F]): F[SecretKey] =
    F.delay(KeyGenerator.getInstance(SigningAlgo).generateKey())

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
    F.delay(new SecretKeySpec(array, SigningAlgo))

}
