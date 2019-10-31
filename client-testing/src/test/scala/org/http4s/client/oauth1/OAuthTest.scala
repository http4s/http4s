package org.http4s.client.oauth1

import cats.effect.IO
import org.http4s._
import org.http4s.client.oauth1
import org.http4s.util.CaseInsensitiveString
import org.specs2.mutable.Specification

class OAuthTest extends Specification {
  // some params taken from http://oauth.net/core/1.0/#anchor30, others from
  // http://tools.ietf.org/html/rfc5849

  val Right(uri) = Uri.fromString("http://photos.example.net/photos")
  val consumer = oauth1.Consumer("dpf43f3p2l4k3l03", "kd94hf93k423kf44")
  val token = oauth1.Token("nnch734d00sl2jdk", "pfkkdhi9sl3r4s00")

  val userParams = Seq(
    "file" -> "vacation.jpg",
    "size" -> "original"
  )

  val allParams = Seq(
    "oauth_consumer_key" -> "dpf43f3p2l4k3l03",
    "oauth_token" -> "nnch734d00sl2jdk",
    "oauth_signature_method" -> "HMAC-SHA1",
    "oauth_timestamp" -> "1191242096",
    "oauth_nonce" -> "kllo9940pd9333jh",
    "oauth_version" -> "1.0"
  ) ++ userParams

  val params2 = Seq(
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

  val specBaseString = "GET&http%3A%2F%2Fphotos.example.net%2Fphotos&file%3Dvacation.jpg%26oauth_consumer_key%" +
    "3Ddpf43f3p2l4k3l03%26oauth_nonce%3Dkllo9940pd9333jh%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%" +
    "3D1191242096%26oauth_token%3Dnnch734d00sl2jdk%26oauth_version%3D1.0%26size%3Doriginal"

  "OAuth support" should {

    "generate a Base String" in {
      oauth1.genBaseString(Method.GET, uri, allParams) must_== specBaseString
    }

    "Generate correct SHA1 signature" in {
      oauth1.makeSHASig(specBaseString, consumer, Some(token)) must_== "tR3+Ty81lMeYAr/Fid0kMTYa/WM="
    }

    "generate a Authorization header" in {
      val auth =
        oauth1.genAuthHeader(Method.GET, uri, userParams, consumer, None, None, Some(token))
      val creds = auth.credentials
      creds.authScheme must_== CaseInsensitiveString("OAuth")
    }
  }

  "RFC 5849 example" should {

    implicit def urlFormEncoder: EntityEncoder[IO, UrlForm] =
      UrlForm.entityEncoder(Charset.`US-ASCII`)

    val Right(uri) = Uri.fromString("http://example.com/request?b5=%3D%253D&a3=a&c%40=&a2=r%20b")
    val Right(body) = UrlForm.decodeString(Charset.`US-ASCII`)("c2&a3=2+q")

    val req = Request[IO](method = Method.POST, uri = uri).withEntity(body)

    "Collect proper params, pg 22" in {
      oauth1.getUserParams(req).unsafeRunSync()._2.sorted must_== Seq(
        "b5" -> "=%3D",
        "a3" -> "a",
        "c@" -> "",
        "a2" -> "r b",
        "c2" -> "",
        "a3" -> "2 q"
      ).sorted
    }
  }
}
