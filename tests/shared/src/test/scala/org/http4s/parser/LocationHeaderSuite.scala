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

import org.http4s.headers.Location

// TODO: this could use more tests
class LocationHeaderSuite extends munit.FunSuite {
  test("LocationHeader parser shouldParse a simple uri") {
    val s = "http://www.foo.com"
    val Right(uri) = Uri.fromString(s): @unchecked
    val hs = Headers(("Location", s))

    val extracted = hs.get[Location]
    assertEquals(extracted, Some(Location(uri)))
  }

  test("LocationHeader parser shouldParse a simple uri with a path but no authority") {
    val s = "http:/foo/bar"
    val Right(uri) = Uri.fromString(s): @unchecked
    val hs = Headers(("Location", s))

    val extracted = hs.get[Location]
    assertEquals(extracted, Some(Location(uri)))
  }

  test("LocationHeader parser shouldParse a relative reference") {
    val s = "/cats"
    val Right(uri) = Uri.fromString(s): @unchecked
    val hs = Headers(("Location", s))

    val extracted = hs.get[Location]
    assertEquals(extracted, Some(Location(uri)))
  }
}
