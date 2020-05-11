/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package parser

import cats.data.NonEmptyList
import org.http4s.headers.Authorization

class AuthorizationHeaderSpec extends Http4sSpec {
  def hparse(value: String) = HttpHeaderParser.AUTHORIZATION(value)

  "Authorization header" should {
    "Parse a valid OAuth2 header" in {
      val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/".toSeq).mkString
      val h = Authorization(Credentials.Token(AuthScheme.Bearer, token + "="))
      hparse(h.value) must beRight(h)
    }

    "Reject an invalid OAuth2 header" in {
      val invalidTokens = Seq("f!@", "=abc", "abc d")
      forall(invalidTokens) { token =>
        val h = Authorization(Credentials.Token(AuthScheme.Bearer, token))
        hparse(h.value) must beLeft
      }
    }

    "Parse a KeyValueCredentials header" in {
      val scheme = "foo"
      val params = NonEmptyList("abc" -> "123", Nil)
      val h = Authorization(Credentials.AuthParams(scheme.ci, params))
      hparse(h.value) must beRight(h)
    }
  }
}
