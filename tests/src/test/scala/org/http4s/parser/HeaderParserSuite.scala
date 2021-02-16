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

import org.typelevel.ci.CIString
import scala.util.Try

class HeaderParserSuite extends Http4sSuite {
  test("Header parsing should catch ParseFailures") {
    val h2 =
      Header.Raw(CIString("Date"), "Fri, 06 Feb 0010 15:28:43 GMT") // Invalid year: must be >= 1800
    assert(Try(h2.parsed).isSuccess)
  }
}
