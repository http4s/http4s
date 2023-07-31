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

import org.http4s.headers.`WWW-Authenticate`

class WwwAuthenticateHeaderSpec extends Http4sSuite with HeaderParserHelper[`WWW-Authenticate`] {

  private val params = Map("a" -> "b", "c" -> "d")
  private val c = Challenge("Basic", "foo")

  private val str = "Basic realm=\"foo\""

  private val wparams = c.copy(params = params)

  test("WWW-Authenticate Header parser should Render challenge correctly") {
    assertEquals(c.renderString, str)
  }

  test("WWW-Authenticate Header parser should Parse a basic authentication") {
    assertEquals(parseOnly(str), `WWW-Authenticate`(c))
  }

  test("WWW-Authenticate Header parser should Parse a basic authentication with params") {
    assertEquals(parseOnly(wparams.renderString), `WWW-Authenticate`(wparams))
  }

  test("WWW-Authenticate Header parser should Parse multiple concatenated authentications") {
    val twotypes = "Newauth realm=\"apps\", Basic realm=\"simple\""
    val twoparsed = Challenge("Newauth", "apps") :: Challenge("Basic", "simple") :: Nil

    assertEquals(parseOnly(twotypes).values.toList, twoparsed)
  }

  test(
    "WWW-Authenticate Header parser should parse multiple concatenated authentications with params"
  ) {
    val twowparams =
      "Newauth realm=\"apps\", type=1, title=\"Login to apps\", Basic realm=\"simple\""
    val twp = Challenge("Newauth", "apps", Map("type" -> "1", "title" -> "Login to apps")) ::
      Challenge("Basic", "simple") :: Nil

    assertEquals(parseOnly(twowparams).values.toList, twp)
  }

}
