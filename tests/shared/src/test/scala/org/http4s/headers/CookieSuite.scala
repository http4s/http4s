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
package headers

class CookieSuite extends Http4sSuite {
  test("parse a simple pair") {
    assertEquals(Cookie.parse("k=v"), Right(Cookie(RequestCookie("k", "v"))))
  }

  test("parse a quoted pair") {
    assertEquals(Cookie.parse("""k="v""""), Right(Cookie(RequestCookie("k", """"v""""))))
  }

  test("parse two pairs") {
    assertEquals(
      Cookie.parse("""k1=v1; k2="v2""""),
      Right(Cookie(RequestCookie("k1", "v1"), RequestCookie("k2", """"v2""""))),
    )
  }

  test("be more tolerant than the spec") {
    assertEquals(
      Cookie.parse("initialTrafficSource=utmcsr=(direct)|utmcmd=(none)|utmccn=(not set);"),
      Right(
        Cookie(
          RequestCookie("initialTrafficSource", "utmcsr=(direct)|utmcmd=(none)|utmccn=(not set)")
        )
      ),
    )
  }

  test("tolerant google sign in cookie") {
    assertEquals(
      Cookie.parse("""g_state={"lo": 1}; k=v"""),
      Right(
        Cookie(
          RequestCookie("g_state", """{"lo": 1}"""),
          RequestCookie("k", "v"),
        )
      ),
    )
  }
}
