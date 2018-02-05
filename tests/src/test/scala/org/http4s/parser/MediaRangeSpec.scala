package org.http4s
package parser

import org.http4s.MediaRange._
import org.http4s.MediaType._
import org.http4s.testing._
import org.specs2.mutable.Specification

class MediaRangeSpec extends Specification with Http4s {
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
      `text/*`.satisfiedBy(`text/css`) must be_==(true)
      `text/*`.satisfiedBy(`text/css`) must be_==(true)
      `text/*`.satisfiedBy(`audio/aiff`) must be_==(false)
    }

    "be satisfied regardless of extensions" in {
      `text/*`.withExtensions(ext).satisfies(`text/*`) must be_==(true)
      `text/*`.withExtensions(ext).satisfies(`text/*`) must be_==(true)
    }
  }

  "MediaTypes" should {

    "Perform equality correctly" in {
      `text/html` must be_==(`text/html`)

      `text/html`.withExtensions(ext) must be_!=(`text/html`)

      `text/html` must be_!=(`text/css`)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/html`.satisfiedBy(`text/css`) must be_==(false)
      `text/html`.satisfiedBy(`text/html`) must be_==(true)

      `text/html`.satisfies(`text/css`) must be_==(false)
    }

    "Not be satisfied by MediaRanges" in {
      `text/html`.satisfiedBy(`text/*`) must be_==(false)
    }

    "Satisfy MediaRanges" in {
      `text/html`.satisfies(`text/*`) must be_==(true)
      `text/*`.satisfies(`text/html`) must be_==(false)
    }

    "be satisfied regardless of extensions" in {
      `text/html`.withExtensions(ext).satisfies(`text/*`) must be_==(true)
      `text/*`.satisfies(`text/html`.withExtensions(ext)) must be_==(false)

      `text/html`.satisfies(`text/*`.withExtensions(ext)) must be_==(true)
      `text/*`.withExtensions(ext).satisfies(`text/html`) must be_==(false)
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
