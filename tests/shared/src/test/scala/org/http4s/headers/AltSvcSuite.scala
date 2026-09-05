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

package org.http4s.headers

import cats.implicits.catsSyntaxEitherId
import cats.implicits.catsSyntaxOptionId
import org.http4s.headers.`Alt-Svc`.AltAuthority
import org.http4s.headers.`Alt-Svc`.AltService
import org.http4s.headers.`Alt-Svc`.Value
import org.http4s.headers.`Alt-Svc`.Value.AltValue
import org.http4s.headers.`Alt-Svc`.Value.Clear
import org.http4s.implicits.http4sSelectSyntaxOne
import org.typelevel.ci.CIStringSyntax

class AltSvcSuite extends HeaderLaws {

  // checkAll("Alt-Svc", headerLaws[`Alt-Svc`])

  test("`Alt-Svc` parses `Clear") {
    assertEquals(`Alt-Svc`.fromString("Clear"), Right(`Alt-Svc`(Clear)))
  }

  test("`Alt-Svc` renders `Clear`") {
    assertEquals(
      `Alt-Svc`(Value.Clear).renderString,
      "Alt-Svc: clear",
    )
  }

  test("`Alt-Svc` renders alternative service") {
    assertEquals(
      `Alt-Svc`(
        AltValue(AltService(`Alt-Svc`.`h2`, AltAuthority(None, 8080), 120L.some, persist = true))
      ).renderString,
      """Alt-Svc: h2=":8080"; ma=120; persist=1""",
    )
  }

  test("`Alt-Svc` renders multiple services") {
    assertEquals(
      `Alt-Svc`(
        AltValue(
          AltService(`Alt-Svc`.`h2`, AltAuthority(None, 8080), 120L.some, persist = true),
          AltService(`Alt-Svc`.`http/1.1`, AltAuthority(ci"mydomain.com".some, 8081), 230L.some),
          AltService(`Alt-Svc`.`h3-25`, AltAuthority(ci"anotherhost.com".some, 8083)),
        )
      ).renderString,
      """Alt-Svc: h2=":8080"; ma=120; persist=1, http/1.1="mydomain.com:8081"; ma=230, h3-25="anotherhost.com:8083"""",
    )
  }

  test("`Alt-Svc` parsers multiple services") {
    assertEquals(
      `Alt-Svc`.fromString(
        """h2=":8080"; ma=120; persist=1, http/1.1="mydomain.com:8081"; ma=230, h3-25="anotherhost.com:8083""""
      ),
      `Alt-Svc`(
        AltValue(
          AltService(`Alt-Svc`.`h2`, AltAuthority(None, 8080), 120L.some, persist = true),
          AltService(`Alt-Svc`.`http/1.1`, AltAuthority(ci"mydomain.com".some, 8081), 230L.some),
          AltService(`Alt-Svc`.`h3-25`, AltAuthority(ci"anotherhost.com".some, 8083)),
        )
      ).asRight[Throwable],
    )
  }
}
