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

package org.http4s.client.oauth1

import org.http4s.client.oauth1.SignatureAlgorithm.Names._
import org.http4s.client.oauth1.ProtocolParameter.SignatureMethod

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.crypto
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SignatureAlgorithm {

  private[oauth1] object Names {

    // algorithms implemented so far
    val `HMAC-SHA1` = "HMAC-SHA1"
    val `HMAC-SHA256` = "HMAC-SHA256"
    val `HMAC-SHA512` = "HMAC-SHA512"

    // other known algorithms (not yet implemented)
    val PLAINTEXT = "PLAINTEXT"
    val `RSA-SHA1` = "RSA-SHA1"
    val `RSA-SHA256` = "RSA-SHA256"
    val `RSA-SHA512` = "RSA-SHA512"
  }

  private[this] val AllMethods = List(HmacSha1, HmacSha256, HmacSha512)

  /**
   * Map a [[SignatureMethod]] protocol parameter to a [[SignatureAlgorithm]] implementation
   *
   * @param method The signature method protocol parameter
   * @return The implementation, or an [[IllegalArgumentException]] if none was found
   */
  private[oauth1] def unsafeFromMethod(method: SignatureMethod): SignatureAlgorithm =
    AllMethods.find(_.name == method.headerValue)
    .getOrElse(throw new IllegalArgumentException(s"Unrecognized headerValue '${method.headerValue}', Valid options are: ${AllMethods.map(_.name)}"))

}

/**
 * Implementations for Oauth1 signatures.
 */
trait SignatureAlgorithm {

  /**
   * @return The signature method name per the oauth1.0 spec
   */
  def name: String

  /**
   * Apply the implementation's algorithm to the input
   *
   * @param input The input value
   * @param secretKey The secret key
   * @return The base64-encoded output
   */
  def generate(input: String, secretKey: String): String

  private[oauth1] def generateHMAC(input: String, algorithm: String, secretKey: String): String = {

    val mac: Mac = crypto.Mac.getInstance(algorithm)
    val sk = new SecretKeySpec(secretKey.getBytes(UTF_8), algorithm)
    mac.init(sk)

    val res = mac.doFinal(input.getBytes(UTF_8))
    Base64.getEncoder.encodeToString(res)

  }
}

/**
 * An implementation of the `HMAC-SHA1` oauth signature method.
 *
 * This uses the `HmacSHA1` implementation which every java platform is required to have.
 */
object HmacSha1 extends SignatureAlgorithm {
  override val name: String = `HMAC-SHA1`
  override def generate(input: String, secretKey: String): String = generateHMAC(input, "HmacSHA1", secretKey)
}

/**
 * An implementation of the `HMAC-SHA256` oauth signature method.
 *
 * This uses the `HmacSHA256` implementation which every java platform is required to have.
 */
object HmacSha256 extends SignatureAlgorithm {
  override val name: String = `HMAC-SHA256`
  override def generate(input: String, secretKey: String): String = generateHMAC(input, "HmacSHA256", secretKey)
}

/**
 * An implementation of the `HMAC-SHA512` oauth signature method.
 *
 * WARNING - This uses the `HmacSHA512` implementation which is *not* required to be present by the Java spec.
 * (However, most modern Java runtimes tend to have it)
 */
object HmacSha512 extends SignatureAlgorithm {
  override val name: String = `HMAC-SHA512`
  override def generate(input: String, secretKey: String): String = generateHMAC(input, "HmacSHA512", secretKey)
}

