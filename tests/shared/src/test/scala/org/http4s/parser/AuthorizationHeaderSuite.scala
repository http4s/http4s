/*
 * Copyright 2013 http4s.org
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

package org.http4s
package parser

import cats.data.NonEmptyList
import org.http4s.headers.Authorization
import org.http4s.syntax.header._
import org.typelevel.ci.CIString

class AuthorizationHeaderSuite extends munit.FunSuite {
  private def hparse(value: String) = Authorization.parse(value)

  test("Authorization header should Parse a valid OAuth2 header") {
    val token = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~+/").mkString
    val h = Authorization(Credentials.Token(AuthScheme.Bearer, token + "="))
    assertEquals(hparse(h.value), Right(h))
  }

  test("Authorization header should Reject an invalid OAuth2 header") {
    val invalidTokens = List("f!@", "=abc", "abc d")
    invalidTokens.foreach { token =>
      val h = Authorization(Credentials.Token(AuthScheme.Bearer, token))
      val Left(_) = hparse(h.value): @unchecked
    }
  }

  test("Authorization header should Parse a KeyValueCredentials header") {
    val scheme = "foo"
    val params = NonEmptyList("abc" -> "123", Nil)
    val h = Authorization(Credentials.AuthParams(CIString(scheme), params))
    assertEquals(hparse(h.value), Right(h))
  }

  test("Authorization header should parse a KeyValueCredentials header unquoted") {
    val scheme = "foo"
    val params = NonEmptyList.of("abc" -> "123")
    val h = Authorization(Credentials.AuthParams(CIString(scheme), params))
    assertEquals(hparse("foo abc=123"), Right(h))
  }

  test("Authorization header should parse a KeyValueCredentials with weird spaces") {
    val scheme = "foo"
    assertEquals(
      hparse("foo abc = \"123 yeah\tyeah yeah\""),
      Right(
        Authorization(
          Credentials.AuthParams(CIString(scheme), NonEmptyList.of("abc" -> "123 yeah\tyeah yeah"))
        )
      ),
    )
    assertEquals(
      // quoted-pair
      hparse("foo abc = \"\\123\""),
      Right(
        Authorization(Credentials.AuthParams(CIString(scheme), NonEmptyList.of("abc" -> "\\123")))
      ),
    )
  }
}
