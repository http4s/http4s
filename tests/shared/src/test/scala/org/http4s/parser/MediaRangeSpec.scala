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

import org.http4s.MediaRange._

class MediaRangeSpec extends Http4sSuite {
  val `text/asp`: MediaType =
    new MediaType("text", "asp", MediaType.Compressible, MediaType.NotBinary, List("asp"))
  val `audio/aiff`: MediaType =
    new MediaType(
      "audio",
      "aiff",
      MediaType.Compressible,
      MediaType.Binary,
      List("aif", "aiff", "aifc"),
    )

  def ext = Map("foo" -> "bar")

  test("MediaRanges should Perform equality correctly") {
    assertEquals(`text/*`, `text/*`)

    assertEquals(`text/*`.withExtensions(ext), `text/*`.withExtensions(ext))
    assertNotEquals(`text/*`.withExtensions(ext), `text/*`)

    assertNotEquals(`text/*`, `audio/*`)
  }

  test("MediaRanges should Be satisfiedBy MediaRanges correctly") {
    assertEquals(`text/*`.satisfiedBy(`text/*`), true)

    assertEquals(`text/*`.satisfiedBy(`image/*`), false)
  }

  test("MediaRanges should Be satisfiedBy MediaTypes correctly") {
    assertEquals(`text/*`.satisfiedBy(MediaType.text.css), true)
    assertEquals(`text/*`.satisfiedBy(MediaType.text.css), true)
    assertEquals(`text/*`.satisfiedBy(`audio/aiff`), false)
  }

  test("MediaRanges should be satisfied regardless of extensions") {
    assertEquals(`text/*`.withExtensions(ext).satisfies(`text/*`), true)
    assertEquals(`text/*`.withExtensions(ext).satisfies(`text/*`), true)
  }

  test("MediaTypes should Perform equality correctly") {
    assertEquals(MediaType.text.html, MediaType.text.html)

    assertNotEquals(MediaType.text.html.withExtensions(ext), MediaType.text.html)

    assertNotEquals(MediaType.text.html, MediaType.text.css)
  }

  test("MediaTypes should Be satisfiedBy MediaTypes correctly") {
    assertEquals(MediaType.text.html.satisfiedBy(MediaType.text.css), false)
    assertEquals(MediaType.text.html.satisfiedBy(MediaType.text.html), true)

    assertEquals(MediaType.text.html.satisfies(MediaType.text.css), false)
  }

  test("MediaTypes should Not be satisfied by MediaRanges") {
    assertEquals(MediaType.text.html.satisfiedBy(`text/*`), false)
  }

  test("MediaTypes should Satisfy MediaRanges") {
    assertEquals(MediaType.text.html.satisfies(`text/*`), true)
    assertEquals(`text/*`.satisfies(MediaType.text.html), false)
  }

  test("MediaTypes should be satisfied regardless of extensions") {
    assertEquals(MediaType.text.html.withExtensions(ext).satisfies(`text/*`), true)
    assertEquals(`text/*`.satisfies(MediaType.text.html.withExtensions(ext)), false)

    assertEquals(MediaType.text.html.satisfies(`text/*`.withExtensions(ext)), true)
    assertEquals(`text/*`.withExtensions(ext).satisfies(MediaType.text.html), false)
  }

  test("MediaRanges and MediaTypes should Do inequality amongst each other properly") {
    val r = `text/*`
    val t = `text/asp`

    assertNotEquals[Any, Any](r, t)
    assertNotEquals[Any, Any](t, r)

    assertNotEquals[Any, Any](r.withExtensions(ext), t.withExtensions(ext))
    assertNotEquals[Any, Any](t.withExtensions(ext), r.withExtensions(ext))
  }
}
