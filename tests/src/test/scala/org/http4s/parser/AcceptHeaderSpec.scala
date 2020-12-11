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
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.specs2.mutable.Specification

class AcceptHeaderSpec extends Specification with HeaderParserHelper[Accept] {
  val `audio/mod`: MediaType =
    new MediaType("audio", "mod", MediaType.Uncompressible, MediaType.Binary, List("mod"))

  def hparse(value: String): ParseResult[Accept] = HttpHeaderParser.ACCEPT(value)

  def ext = Map("foo" -> "bar", "baz" -> "whatever")

  "Accept-Header parser" should {
    "Parse all registered MediaRanges" in {
      // Parse a single one
      parse("image/*").values.head must be_==~(`image/*`)

      // Parse the rest
      foreach(MediaRange.standard.values) { m =>
        val r = parse(m.show).values.head
        r must be_===(MediaRangeAndQValue(m))
      }
    }

    "Deal with '.' and '+' chars" in {
      val value = "application/soap+xml, application/vnd.ms-fontobject"
      val accept =
        Accept(MediaType.application.`soap+xml`, MediaType.application.`vnd.ms-fontobject`)
      parse(value) must be_===(accept)
    }

    "Parse all registered MediaTypes" in {
      // Parse a single one
      parse("image/jpeg").values.head must be_==~(MediaType.image.jpeg)

      // Parse the rest
      foreach(MediaType.all.values) { m =>
        val r = parse(m.show).values.head
        r must be_===(MediaRangeAndQValue(m))
      }
    }

    "Parse multiple Ranges" in {
      // Just do a single type
      val accept = Accept(`audio/*`, `video/*`)
      parse(accept.value) must be_===(accept)

      val accept2 = Accept(`audio/*`.withQValue(QValue.q(0.2)), `video/*`)
      parse(accept2.value) must be_===(accept2)

      // Go through all of them
      {
        val samples = MediaRange.standard.values.map(MediaRangeAndQValue(_))
        foreach(samples.sliding(4).toArray) { sample =>
          val h = Accept(sample.head, sample.tail.toSeq: _*)
          parse(h.value) must be_===(h)
        }
      }

      // Go through all of them with q and extensions
      {
        val samples =
          MediaRange.standard.values.map(_.withExtensions(ext).withQValue(QValue.q(0.2)))
        foreach(samples.sliding(4).toArray) { sample =>
          val h = Accept(sample.head, sample.tail.toSeq: _*)
          parse(h.value) must be_===(h)
        }
      }
    }

    "Parse multiple Types" in {
      // Just do a single type
      val accept = Accept(`audio/mod`, MediaType.audio.mpeg)
      parse(accept.value) must be_===(accept)

      // Go through all of them
      val samples = MediaType.all.values.map(MediaRangeAndQValue(_))
      foreach(samples.sliding(4).toArray) { sample =>
        val h = Accept(sample.head, sample.tail.toSeq: _*)
        parse(h.value) must be_===(h)
      }
    }

    "Deal with q and extensions" in {
      val value = "text/*;q=0.3, text/html;q=0.7, text/html;level=1"
      parse(value) must be_===(
        Accept(
          `text/*`.withQValue(QValue.q(0.3)),
          MediaType.text.html.withQValue(QValue.q(0.7)),
          MediaType.text.html.withExtensions(Map("level" -> "1"))
        ))

      // Go through all of them
      val samples = MediaType.all.values.map(_.withExtensions(ext).withQValue(QValue.q(0.2)))
      foreach(samples.sliding(4).toArray) { sample =>
        val h = Accept(sample.head, sample.tail.toSeq: _*)
        parse(h.value) must be_===(h)
      }
    }
  }
}
