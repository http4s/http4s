package org.http4s
package parser

import org.http4s.headers.Authorization
import org.http4s.util.NonEmptyList
import org.http4s.util.string._

class AuthorizationHeaderSpec extends Http4sSpec {

  def hparse(value: String) = HttpHeaderParser.AUTHORIZATION(value)

  "Authorization header" should {
    "Parse a valid OAuth2 header" in {
      val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/".toSeq).mkString
      val h = Authorization(Credentials.Token(AuthScheme.Bearer, token + "="))
      hparse(h.value.toString) must be_\/-(h)
    }

    "Reject an invalid OAuth2 header" in {
      val invalidTokens = Seq("f!@", "=abc", "abc d")
      forall(invalidTokens) { token =>
        val h = Authorization(Credentials.Token(AuthScheme.Bearer, token))
        hparse(h.value.toString) must be_-\/
      }
    }

    "Parse a KeyValueCredentials header" in {
      val scheme = "foo"
      val params = NonEmptyList("abc" -> "123")
      val h = Authorization(Credentials.AuthParams(scheme.ci, params))
      hparse(h.value.toString) must be_\/-(h)
    }
  }
}
