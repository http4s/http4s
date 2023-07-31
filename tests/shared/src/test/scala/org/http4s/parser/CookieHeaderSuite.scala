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
import cats.syntax.all._

class CookieHeaderSuite extends munit.FunSuite {
  private def parse(value: String) = headers.Cookie.parse(value).valueOr(throw _)

  private val cookiestr = "key1=value1; key2=\"value2\""
  private val cookiestrSemicolon: String = cookiestr + ";"
  private val cookies = List(RequestCookie("key1", "value1"), RequestCookie("key2", """"value2""""))

  test("Cookie parser should parse a cookie") {
    assertEquals(parse(cookiestr).values.toList, cookies)
  }
  test("Cookie parser should parse a cookie (semicolon at the end)") {
    assertEquals(parse(cookiestrSemicolon).values.toList, cookies)
  }
  test("Cookie parser should tolerate spaces") {
    assertEquals(
      parse("initialTrafficSource=utmcsr=(direct)|utmcmd=(none)|utmccn=(not set);").values,
      NonEmptyList.one(
        RequestCookie("initialTrafficSource", "utmcsr=(direct)|utmcmd=(none)|utmccn=(not set)")
      ),
    )
  }
}
