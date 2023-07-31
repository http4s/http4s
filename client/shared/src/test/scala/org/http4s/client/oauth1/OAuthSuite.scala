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

import cats.data.NonEmptyList
import cats.effect.IO
import org.http4s.Credentials.AuthParams
import org.http4s._
import org.http4s.client.oauth1
import org.http4s.client.oauth1.ProtocolParameter._
import org.http4s.client.oauth1.SignatureAlgorithm.Names._
import org.http4s.headers.Authorization

class OAuthSuite extends Http4sSuite {
  // some params taken from http://oauth.net/core/1.0/#anchor30, others from
  // https://datatracker.ietf.org/doc/html/rfc5849

  private val Right(uri) = Uri.fromString("http://photos.example.net/photos")
  private val consumer = oauth1.Consumer("dpf43f3p2l4k3l03", "kd94hf93k423kf44")
  private val token = oauth1.Token("nnch734d00sl2jdk", "pfkkdhi9sl3r4s00")

  private val userParams = List(
    "file" -> "vacation.jpg",
    "size" -> "original",
  )

  private val allParams = List(
    "oauth_consumer_key" -> "dpf43f3p2l4k3l03",
    "oauth_token" -> "nnch734d00sl2jdk",
    "oauth_signature_method" -> `HMAC-SHA1`,
    "oauth_timestamp" -> "1191242096",
    "oauth_nonce" -> "kllo9940pd9333jh",
    "oauth_version" -> "1.0",
  ) ++ userParams

  private val specBaseString =
    "GET&http%3A%2F%2Fphotos.example.net%2Fphotos&file%3Dvacation.jpg%26oauth_consumer_key%" +
      "3Ddpf43f3p2l4k3l03%26oauth_nonce%3Dkllo9940pd9333jh%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%" +
      "3D1191242096%26oauth_token%3Dnnch734d00sl2jdk%26oauth_version%3D1.0%26size%3Doriginal"

  test("OAuth support should generate a Base String") {
    assertEquals(oauth1.genBaseString(Method.GET, uri, allParams), specBaseString)
  }

  test("OAuth support should generate a correct HMAC-SHA1 signature") {
    assertIO(
      oauth1.makeSHASig[IO](specBaseString, consumer.secret, Some(token.secret), HmacSha1),
      "tR3+Ty81lMeYAr/Fid0kMTYa/WM=",
    )
  }

  test("OAuth support should generate a Authorization header with config") {
    oauth1
      .genAuthHeader[IO](
        Method.GET,
        uri,
        oauth1.ProtocolParameter.Consumer("dpf43f3p2l4k3l03", "kd94hf93k423kf44"),
        Some(oauth1.ProtocolParameter.Token("nnch734d00sl2jdk", "pfkkdhi9sl3r4s00")),
        realm = Some(Realm("Example")),
        signatureMethod = SignatureMethod(),
        timestampGenerator = Timestamp.now[IO],
        version = Version(),
        nonceGenerator = Nonce.now[IO],
        callback = None,
        verifier = None,
        userParams.map { case (k, v) => Custom(k, v) },
      )
      .map { auth =>
        val creds = auth.credentials
        assertEquals(creds.authScheme, AuthScheme.OAuth)
      }
  }

  test("RFC 5849 example shouldCollect proper params, pg 22") {
    implicit def urlFormEncoder: EntityEncoder[IO, UrlForm] =
      UrlForm.entityEncoder(Charset.`US-ASCII`)

    val Right(uri) = Uri.fromString("http://example.com/request?b5=%3D%253D&a3=a&c%40=&a2=r%20b")
    val Right(body) = UrlForm.decodeString(Charset.`US-ASCII`)("c2&a3=2+q")

    val req = Request[IO](method = Method.POST, uri = uri).withEntity(body)

    oauth1.getUserParams(req).map { case (_, v) =>
      assertEquals(
        v.sorted,
        Vector(
          "b5" -> "=%3D",
          "a3" -> "a",
          "c@" -> "",
          "a2" -> "r b",
          "c2" -> "",
          "a3" -> "2 q",
        ).sorted,
      )
    }
  }

  test("signRequest should sign with HMAC-SHA1 by default") {
    signRequestWith(
      method = SignatureMethod(),
      expectedAlgorithm = `HMAC-SHA1`,
      expectedSignature = "dzirhDxkLGCZEH/LnVin6zoalUk=",
    )
  }

  test("signRequest should sign with HMAC-SHA1") {
    signRequestWith(
      method = SignatureMethod(`HMAC-SHA1`),
      expectedAlgorithm = `HMAC-SHA1`,
      expectedSignature = "dzirhDxkLGCZEH/LnVin6zoalUk=",
    )
  }

  test("signRequest should sign with HMAC-SHA256") {
    signRequestWith(
      method = SignatureMethod(`HMAC-SHA256`),
      expectedAlgorithm = `HMAC-SHA256`,
      expectedSignature = "gzBSlXIQTJyfbzwFv3+4sXZlE6Jh6g/yfq4CB/StKSA=",
    )
  }

  test("signRequest should sign with HMAC-SHA512") {
    signRequestWith(
      method = SignatureMethod(`HMAC-SHA512`),
      expectedAlgorithm = `HMAC-SHA512`,
      expectedSignature =
        "7ZO6N+8QMQAPjBbBPJsRmUD11jd5bL7ldwg+ObOFyBqKN0vEFiv2ItlrO2Oly68K7k63whUlsu0f0a/6uAHSxw==",
    )
  }

  // This is a useful tool for verifying signature values: http://lti.tools/oauth/
  def signRequestWith(
      method: SignatureMethod,
      expectedAlgorithm: String,
      expectedSignature: String,
  ): IO[Unit] = {

    def fixedTS: IO[Timestamp] = IO(Timestamp("1628332200"))
    def fixedNonce: IO[Nonce] = IO(Nonce("123456789"))
    val Right(uri) = Uri.fromString("http://www.peepandthebigwideworld.com/")

    oauth1
      .signRequest(
        req = Request[IO](Method.GET, uri),
        oauth1.ProtocolParameter.Consumer("quack's-consumer-key", "i-heart-my-pond"),
        Some(oauth1.ProtocolParameter.Token("quack's-token", "ducks-are-the-best")),
        realm = None,
        signatureMethod = method,
        timestampGenerator = fixedTS,
        version = Version(),
        nonceGenerator = fixedNonce,
        callback = None,
        verifier = None,
      )
      .map { req =>
        val expectedSigEncoded = Uri.encode(expectedSignature, toSkip = Uri.Unreserved)

        assertEquals(
          req.headers.get[Authorization],
          Some(
            Authorization(
              credentials = AuthParams(
                authScheme = AuthScheme.OAuth,
                params = NonEmptyList.of(
                  "oauth_signature" -> expectedSigEncoded,
                  "oauth_consumer_key" -> "quack%27s-consumer-key",
                  "oauth_signature_method" -> expectedAlgorithm,
                  "oauth_timestamp" -> "1628332200",
                  "oauth_nonce" -> "123456789",
                  "oauth_version" -> "1.0",
                  "oauth_token" -> "quack%27s-token",
                ),
              )
            )
          ),
        )
      }

  }
}
