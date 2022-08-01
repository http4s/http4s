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

import cats.syntax.show._
import org.http4s.MediaRange._
import org.http4s.MediaType._
import org.http4s.headers.Accept
import org.http4s.headers.MediaRangeAndQValue
import org.http4s.syntax.all._

class AcceptHeaderSpec extends Http4sSuite with HeaderParserHelper[Accept] {

  val `audio/mod`: MediaType =
    new MediaType("audio", "mod", MediaType.Uncompressible, MediaType.Binary, List("mod"))

  def ext = Map("foo" -> "bar", "baz" -> "whatever")

  test("Accept-Header parser should Parse all registered MediaRanges") {
    // Parse a single one
    assertEquals(parse("image/*").values.head.mediaRange, `image/*`)

    // Parse the rest
    MediaRange.standard.values.foreach { m =>
      val r = parse(m.show).values.head
      assertEquals(r, MediaRangeAndQValue(m))
    }
  }

  test("Accept-Header parser should Deal with '.' and '+' chars") {
    val value = "application/soap+xml, application/vnd.ms-fontobject"
    val accept =
      Accept(MediaType.application.`soap+xml`, MediaType.application.`vnd.ms-fontobject`)
    assertEquals(parse(value), accept)
  }

  test("Accept-Header parser should Parse all registered MediaTypes") {
    // Parse a single one
    assertEquals(parse("image/jpeg").values.head.mediaRange, MediaType.image.jpeg)

    // Parse the rest
    MediaType.all.values.foreach { m =>
      val r = parse(m.show).values.head
      assertEquals(r, MediaRangeAndQValue(m))
    }
  }

  test("Accept-Header parser should Parse multiple Ranges") {
    // Just do a single type
    val accept = Accept(`audio/*`, `video/*`)
    assertEquals(roundTrip(accept), accept)

    val accept2 = Accept(`audio/*`.withQValue(qValue"0.2"), `video/*`)
    assertEquals(roundTrip(accept2), accept2)

    // Go through all of them
    {
      val samples = MediaRange.standard.values.map(MediaRangeAndQValue(_)).toList
      samples.sliding(4).foreach { sample =>
        val h = Accept(sample.head, sample.tail: _*)
        assertEquals(roundTrip(h), h)
      }
    }

    // Go through all of them with q and extensions
    {
      val samples =
        MediaRange.standard.values.map(_.withExtensions(ext).withQValue(qValue"0.2")).toList
      samples.sliding(4).foreach { sample =>
        val h = Accept(sample.head, sample.tail: _*)
        assertEquals(roundTrip(h), h)
      }
    }
  }

  test("Accept-Header parser should Parse multiple Types") {
    // Just do a single type
    val accept = Accept(`audio/mod`, MediaType.audio.mpeg)
    assertEquals(roundTrip(accept), accept)

    // Go through all of them
    val samples = MediaType.all.values.map(MediaRangeAndQValue(_)).toList
    samples.sliding(4).foreach { sample =>
      val h = Accept(sample.head, sample.tail: _*)
      assertEquals(roundTrip(h), h)
    }
  }

  test("Accept-Header parser should Deal with q and extensions") {
    val value = "text/*;q=0.3, text/html;q=0.7, text/html;level=1"
    assertEquals(
      parse(value),
      Accept(
        `text/*`.withQValue(qValue"0.3"),
        MediaType.text.html.withQValue(qValue"0.7"),
        MediaType.text.html.withExtensions(Map("level" -> "1")),
      ),
    )

    // Go through all of them
    val samples = MediaType.all.values.map(_.withExtensions(ext).withQValue(qValue"0.2")).toList
    samples.sliding(4).foreach { sample =>
      val h = Accept(sample.head, sample.tail: _*)
      assertEquals(roundTrip(h), h)
    }
  }

}
