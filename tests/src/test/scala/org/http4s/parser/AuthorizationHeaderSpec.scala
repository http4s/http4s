package org.http4s.parser

import org.http4s.{Http4sSpec, OAuth2BearerToken, GenericCredentials}
import org.http4s.headers.Authorization
import org.http4s.util.string._

class AuthorizationHeaderSpec extends Http4sSpec {

  def hparse(value: String) = HttpHeaderParser.AUTHORIZATION(value)

  "Authorization header" should {
    "Parse a valid Oauth2 header" in {
      val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/".toSeq).mkString
      val h = Authorization(OAuth2BearerToken(token + "="))
      hparse(h.value) must be_\/-(h)
    }

    "Reject an invalid Oauth2 header" in {
      val invalidTokens = Seq("f!@", "=abc", "abc d")
      forall(invalidTokens) { token =>
        val h = Authorization(OAuth2BearerToken(token))
        hparse(h.value) must be_-\/
      }
    }

    "Parse a GenericCredential header" in {
      val scheme = "token"
      val token = "adsfafsf2332fasdad322332"
      val h = Authorization(GenericCredentials(scheme.ci, token))
      hparse(s"$scheme $token") must be_\/-(h)
    }
  }
}
