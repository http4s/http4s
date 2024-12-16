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

import cats.effect.IO
import org.http4s.Http4sSuite
import org.http4s.client.oauth1.ProtocolParameter.SignatureMethod
import org.http4s.client.oauth1.SignatureAlgorithm.Names._

class SignatureAlgorithmSuite extends Http4sSuite {

  val InputString: String = "Lisa Needs Braces!"
  val SecretKey: String = "Dental Plan!"

  test("SignatureMethod protocol parameter should default to HmacSha1") {
    assertEquals(SignatureAlgorithm.unsafeFromMethod(SignatureMethod()), HmacSha1)
  }

  test("SignatureMethod protocol parameter should find appropriate implementation") {
    assertEquals(SignatureAlgorithm.unsafeFromMethod(SignatureMethod(`HMAC-SHA1`)), HmacSha1)
    assertEquals(SignatureAlgorithm.unsafeFromMethod(SignatureMethod(`HMAC-SHA256`)), HmacSha256)
    assertEquals(SignatureAlgorithm.unsafeFromMethod(SignatureMethod(`HMAC-SHA512`)), HmacSha512)
  }

  test("SignatureMethod protocol parameter should result in error if implementation not found") {
    intercept[IllegalArgumentException] {
      SignatureAlgorithm.unsafeFromMethod(SignatureMethod("QUACK"))
    }
  }

  test("HmacSha1 should generate valid Base64 signature") {
    assertIO(HmacSha1.generateBase64[IO](InputString, SecretKey), "jxlA9wGy7ywMXFH6gmOrGD8fVGo=")
  }

  test("HmacSha256 should generate valid Base64 signature") {
    assertIO(
      HmacSha256.generateBase64[IO](InputString, SecretKey),
      "J2LpHzGwjVR8x0ZbbNC2ehI3UwfBp4xu9SUNAVhXrrM=",
    )
  }

  test("HmacSha512 should generate valid Base64 signature") {
    assertIO(
      HmacSha512.generateBase64[IO](InputString, SecretKey),
      "aa5p3aIHcy5525TKAn/y3tG+lwcSBtawNO4d6ScVAJf2/bsQ9uJxVzMQhA1I68rRuk0Jie/V39yUlTAoR1+1Sw==",
    )
  }
  test("HmacSha1 should generate valid Hexadecimal signature") {
    assertIO(
      HmacSha1.generateHex[IO](InputString, SecretKey),
      "8f1940f701b2ef2c0c5c51fa8263ab183f1f546a",
    )
  }

  test("HmacSha256 should generate valid Hexadecimal signature") {
    assertIO(
      HmacSha256.generateHex[IO](InputString, SecretKey),
      "2762e91f31b08d547cc7465b6cd0b67a12375307c1a78c6ef5250d015857aeb3",
    )
  }

  test("HmacSha512 should generate valid Hexadecimal signature") {
    assertIO(
      HmacSha512.generateHex[IO](InputString, SecretKey),
      "69ae69dda207732e79db94ca027ff2ded1be97071206d6b034ee1de927150097f6fdbb10f6e271573310840d48ebcad1ba4d0989efd5dfdc94953028475fb54b",
    )
  }

}
