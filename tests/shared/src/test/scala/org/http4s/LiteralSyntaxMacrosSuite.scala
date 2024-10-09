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

import org.http4s.implicits._

class LiteralSyntaxMacrosSuite extends Http4sSuite {
  test("'uri' macro works for valid input") {
    assertEquals(uri"a.b.c", Uri.fromString("a.b.c").toOption.get)
  }
  test("'uri' macro works with variable named 'org'") {
    val org = "blerf"
    assertEquals(uri"org" / org, Uri.fromString("org/blerf").toOption.get)
  }

  test("'uri' macro works with implicit variable named 'org' in scope") {
    implicit val org = "the-problem"
    assertEquals(uri"example", uri"example")
  }
  test("invalid uri won't compile") {
    assert(
      compileErrors {
        """uri"     " // doesn't compile, not parsable as a Uri"""
      }.nonEmpty
    )
  }
  test("'path' macro works for valid input") {
    assertEquals(path"/foo/bar", path"/foo/bar")
  }
  test("'scheme' macro works for valid input") {
    assertEquals(scheme"https", Uri.Scheme.unsafeFromString("https"))
  }
  test("invalid scheme won't compile") {
    assert(
      compileErrors {
        """scheme"     " // doesn't compile, not parsable as a Scheme"""
      }.nonEmpty
    )
  }
  test("'mediaType' macro works for valid input") {
    assertEquals(mediaType"application/json", MediaType.unsafeParse("application/json"))
  }
  test("invalid mediaType won't compile") {
    assert(
      compileErrors {
        """mediaType"     " // doesn't compile, not parsable as a MediaType"""
      }.nonEmpty
    )
  }
  test("'qValue' macro works for valid input") {
    assertEquals(qValue"0.5", QValue.unsafeFromString("0.5"))
  }
  test("invalid qValue won't compile") {
    assert(
      compileErrors {
        """qValue"     " // doesn't compile, not parsable as a QValue"""
      }.nonEmpty
    )
  }
}
