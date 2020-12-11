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
import org.http4s.MediaType
import org.specs2.mutable.Specification

class MediaRangeSpec extends Specification {
  val `text/asp`: MediaType =
    new MediaType("text", "asp", MediaType.Compressible, MediaType.NotBinary, List("asp"))
  val `audio/aiff`: MediaType =
    new MediaType(
      "audio",
      "aiff",
      MediaType.Compressible,
      MediaType.Binary,
      List("aif", "aiff", "aifc"))

  def ext = Map("foo" -> "bar")

  "MediaRanges" should {
    "Perform equality correctly" in {
      `text/*` must be_==(`text/*`)

      `text/*`.withExtensions(ext) must be_==(`text/*`.withExtensions(ext))
      `text/*`.withExtensions(ext) must be_!=(`text/*`)

      `text/*` must be_!=(`audio/*`)
    }

    "Be satisfiedBy MediaRanges correctly" in {
      `text/*`.satisfiedBy(`text/*`) must be_==(true)

      `text/*`.satisfiedBy(`image/*`) must be_==(false)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/*`.satisfiedBy(MediaType.text.css) must be_==(true)
      `text/*`.satisfiedBy(MediaType.text.css) must be_==(true)
      `text/*`.satisfiedBy(`audio/aiff`) must be_==(false)
    }

    "be satisfied regardless of extensions" in {
      `text/*`.withExtensions(ext).satisfies(`text/*`) must be_==(true)
      `text/*`.withExtensions(ext).satisfies(`text/*`) must be_==(true)
    }
  }

  "MediaTypes" should {
    "Perform equality correctly" in {
      MediaType.text.html must be_==(MediaType.text.html)

      MediaType.text.html.withExtensions(ext) must be_!=(MediaType.text.html)

      MediaType.text.html must be_!=(MediaType.text.css)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      MediaType.text.html.satisfiedBy(MediaType.text.css) must be_==(false)
      MediaType.text.html.satisfiedBy(MediaType.text.html) must be_==(true)

      MediaType.text.html.satisfies(MediaType.text.css) must be_==(false)
    }

    "Not be satisfied by MediaRanges" in {
      MediaType.text.html.satisfiedBy(`text/*`) must be_==(false)
    }

    "Satisfy MediaRanges" in {
      MediaType.text.html.satisfies(`text/*`) must be_==(true)
      `text/*`.satisfies(MediaType.text.html) must be_==(false)
    }

    "be satisfied regardless of extensions" in {
      MediaType.text.html.withExtensions(ext).satisfies(`text/*`) must be_==(true)
      `text/*`.satisfies(MediaType.text.html.withExtensions(ext)) must be_==(false)

      MediaType.text.html.satisfies(`text/*`.withExtensions(ext)) must be_==(true)
      `text/*`.withExtensions(ext).satisfies(MediaType.text.html) must be_==(false)
    }
  }

  "MediaRanges and MediaTyes" should {
    "Do inequality amongst each other properly" in {
      val r = `text/*`
      val t = `text/asp`

      r must be_!=(t)
      t must be_!=(r)

      r.withExtensions(ext) must be_!=(t.withExtensions(ext))
      t.withExtensions(ext) must be_!=(r.withExtensions(ext))
    }
  }
}
