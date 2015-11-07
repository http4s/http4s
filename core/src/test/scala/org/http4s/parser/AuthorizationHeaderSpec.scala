package org.http4s.parser

import org.http4s.{Http4sSpec, OAuth2BearerToken}
import org.http4s.headers.Authorization


class AuthorizationHeaderSpec extends Http4sSpec {

  def hparse(value: String) = HttpHeaderParser.AUTHORIZATION(value)

  "Authorization header" should {
    "Parse a valid Oauth2 header" in {
      val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/".toSeq).mkString
      val h = Authorization(OAuth2BearerToken(token + "="))
      hparse(h.value) must be_\/-(h)
    }

    "Reject an ivalid Oauth2 header" in {
      val invalidTokens = Seq("f!@", "=abc", "abc d")
      forall(invalidTokens) { token =>
        val h = Authorization(OAuth2BearerToken(token))
        hparse(h.value) must be_-\/
      }
    }
  }
}
