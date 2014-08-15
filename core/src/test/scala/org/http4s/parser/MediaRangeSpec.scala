package org.http4s
package parser

import org.http4s.MediaRange._
import org.http4s.MediaType._
import org.specs2.mutable.Specification

class MediaRangeSpec extends Specification with Http4s {

  def ext = Map("foo" -> "bar")

  "MediaRanges" should {

    "Perform equality correctly" in {
      `text/*` must be_==(`text/*`)

      `text/*`.withQValue(q(0.4)) must be_==(`text/*`.withQValue(q(0.4)))
      `text/*`.withQValue(q(0.4)).withExtensions(ext) must be_==(`text/*`.withQValue(q(0.4)).withExtensions(ext))
      `text/*`.withQValue(q(0.4)).withExtensions(ext) must be_!=(`text/*`.withQValue(q(0.4)))

      `text/*`.withQValue(q(0.1)) must be_!=(`text/*`.withQValue(q(0.4)))
      `text/*`.withQValue(q(0.4)) must be_!=(`text/*`.withQValue(q(0.1)))

      `text/*` must be_!=(`audio/*`)
    }

    "Be satisfiedBy MediaRanges correctly" in {
      `text/*`.withQValue(q(0.5)).satisfiedBy(`text/*`.withQValue(q(0.4))) must be_==(false)
      `text/*`.withQValue(q(0.3)).satisfiedBy(`text/*`.withQValue(q(0.4))) must be_==(true)

      `text/*`.satisfiedBy(`image/*`) must be_==(false)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/*`.satisfiedBy(`text/css`) must be_==(true)
      `text/*`.satisfiedBy(`text/css`.withQValue(q(0.2))) must be_==(false)
      `text/*`.withQValue(q(0.2)).satisfiedBy(`text/css`) must be_==(true)
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
      `text/html`.withQValue(q(0.4)) must be_==(`text/html`.withQValue(q(0.4)))

      `text/html`.withQValue(q(0.4)).withExtensions(ext) must be_!=(`text/html`.withQValue(q(0.4)))
      `text/html`.withQValue(q(0.4)) must be_!=(`text/html`.withQValue(q(0.4)).withExtensions(ext))


      `text/html`.withQValue(q(0.1)) must be_!=(`text/html`.withQValue(q(0.4)))
      `text/html`.withQValue(q(0.4)) must be_!=(`text/html`.withQValue(q(0.1)))

      `text/html` must be_!=(`text/css`)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/html`.satisfiedBy(`text/css`) must be_==(false)
      `text/html`.withQValue(q(0.5)).satisfiedBy(`text/html`.withQValue(q(0.4))) must be_==(false)
      `text/html`.withQValue(q(0.3)).satisfiedBy(`text/html`.withQValue(q(0.4))) must be_==(true)

      `text/html`.satisfies(`text/css`) must be_==(false)
      `text/html`.withQValue(q(0.5)).satisfies(`text/html`.withQValue(q(0.4))) must be_==(true)
      `text/html`.withQValue(q(0.3)).satisfies(`text/html`.withQValue(q(0.4))) must be_==(false)
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

      r.withQValue(q(0.1)) must be_!=(t.withQValue(q(0.1)))
      t.withQValue(q(0.1)) must be_!=(r.withQValue(q(0.1)))

      r.withExtensions(ext) must be_!=(t.withExtensions(ext))
      t.withExtensions(ext) must be_!=(r.withExtensions(ext))
    }
  }
}
