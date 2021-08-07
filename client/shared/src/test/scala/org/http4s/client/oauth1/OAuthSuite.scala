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
import org.http4s._
import org.http4s.client.oauth1
import org.http4s.client.oauth1.ProtocolParameter.{
  Custom,
  Nonce,
  Realm,
  SignatureMethod,
  Timestamp,
  Version
}
import org.typelevel.ci._

class OAuthSuite extends Http4sSuite {
  // some params taken from http://oauth.net/core/1.0/#anchor30, others from
  // http://tools.ietf.org/html/rfc5849

  val Right(uri) = Uri.fromString("http://photos.example.net/photos")
  val consumer = oauth1.Consumer("dpf43f3p2l4k3l03", "kd94hf93k423kf44")
  val token = oauth1.Token("nnch734d00sl2jdk", "pfkkdhi9sl3r4s00")

  val userParams = List(
    "file" -> "vacation.jpg",
    "size" -> "original"
  )

  val allParams = List(
    "oauth_consumer_key" -> "dpf43f3p2l4k3l03",
    "oauth_token" -> "nnch734d00sl2jdk",
    "oauth_signature_method" -> "HMAC-SHA1",
    "oauth_timestamp" -> "1191242096",
    "oauth_nonce" -> "kllo9940pd9333jh",
    "oauth_version" -> "1.0"
  ) ++ userParams

  val params2 = List(
    "b5" -> Some("=%3D"),
    "a3" -> Some("a"),
    "c@" -> None,
    "a2" -> Some("r b"),
    "oauth_consumer_key" -> Some("9djdj82h48djs9d2"),
    "oauth_token" -> Some("kkk9d7dh3k39sjv7"),
    "oauth_signature_method" -> Some("HMAC-SHA1"),
    "oauth_timestamp" -> Some("137131201"),
    "oauth_nonce" -> Some("7d8f3e4a"),
    "c2" -> None,
    "a3" -> Some("2 q")
  )

  val specBaseString =
    "GET&http%3A%2F%2Fphotos.example.net%2Fphotos&file%3Dvacation.jpg%26oauth_consumer_key%" +
      "3Ddpf43f3p2l4k3l03%26oauth_nonce%3Dkllo9940pd9333jh%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%" +
      "3D1191242096%26oauth_token%3Dnnch734d00sl2jdk%26oauth_version%3D1.0%26size%3Doriginal"

  test("OAuth support should generate a Base String") {
    assert(oauth1.genBaseString(Method.GET, uri, allParams) == specBaseString)
  }

  test("OAuth support should generate a correct SHA1 signature") {
    assertIO(
      oauth1.makeSHASig[IO](specBaseString, consumer, Some(token)),
      "tR3+Ty81lMeYAr/Fid0kMTYa/WM=")
  }

  test("OAuth support should generate a Authorization header") {
    val auth =
      oauth1.genAuthHeader[IO](Method.GET, uri, userParams, consumer, None, None, Some(token))
    val creds = auth.map(_.credentials)
    assertIO(creds.map(_.authScheme), ci"OAuth")
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
        userParams.map { case (k, v) => Custom(k, v) }
      )
      .map { auth =>
        val creds = auth.credentials
        assert(creds.authScheme == ci"OAuth")
      }
  }

  test("RFC 5849 example shouldCollect proper params, pg 22") {
    implicit def urlFormEncoder: EntityEncoder[IO, UrlForm] =
      UrlForm.entityEncoder(Charset.`US-ASCII`)

    val Right(uri) = Uri.fromString("http://example.com/request?b5=%3D%253D&a3=a&c%40=&a2=r%20b")
    val Right(body) = UrlForm.decodeString(Charset.`US-ASCII`)("c2&a3=2+q")

    val req = Request[IO](method = Method.POST, uri = uri).withEntity(body)

    oauth1.getUserParams(req).map { case (_, v) =>
      assert(
        v.sorted == Seq(
          "b5" -> "=%3D",
          "a3" -> "a",
          "c@" -> "",
          "a2" -> "r b",
          "c2" -> "",
          "a3" -> "2 q"
        ).sorted)
    }
  }
}
