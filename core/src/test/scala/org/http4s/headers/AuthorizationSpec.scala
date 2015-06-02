package org.http4s
package headers


class AuthorizationSpec extends HeaderParserSpec(Authorization) {

  "Authorization header" should {
    "Parse a valid Oauth2 header" in {
      val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/".toSeq).mkString
      val h = Authorization(OAuth2BearerToken(token + "="))
      hparse(h.value) must_== Some(h)
    }

    "Reject an invalid Oauth2 header" in {
      val invalidTokens = Seq("f!@", "=abc", "abc d")
      forall(invalidTokens) { token =>
        val h = Authorization(OAuth2BearerToken(token))
        hparse(h.value) must_== None
      }
    }
  }
}
