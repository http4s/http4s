package org.http4s
package client

import org.specs2.mutable.Specification

import scalaz.\/-


class OAuthTest extends Specification {

  val \/-(uri) = Uri.fromString("http://photos.example.net/photos")

  // params taken from http://oauth.net/core/1.0/#anchor30

  // kd94hf93k423kf44&pfkkdhi9sl3r4s00
  val consumer = oauth.Consumer("dpf43f3p2l4k3l03", "kd94hf93k423kf44")
  val token    = oauth.Token("nnch734d00sl2jdk", "pfkkdhi9sl3r4s00")
  
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

  val specBaseString = "GET&http%3A%2F%2Fphotos.example.net%2Fphotos&file%3Dvacation.jpg%26oauth_consumer_key%3Ddpf43f3p2l4k3l03%26oauth_nonce%3Dkllo9940pd9333jh%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%3D1191242096%26oauth_token%3Dnnch734d00sl2jdk%26oauth_version%3D1.0%26size%3Doriginal"

  "OAuth support" should {

    "generate a Base String" in {
      oauth.genBaseString(Method.GET, uri, allParams) must_== specBaseString
    }

    "Generate correct SHA1 signature" in {
      oauth.makeSHASig(specBaseString, consumer, Some(token)) must_== "tR3+Ty81lMeYAr/Fid0kMTYa/WM="
    }

    "generate a Authorization header" in {
      val auth = oauth.genAuthHeader(Method.GET, uri, userParams, consumer, None, None, Some(token))
      println(auth)
      true must_== true
    }
  }

}
