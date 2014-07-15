package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.MediaRange._
import org.http4s.MediaType._

class MediaRangeSpec extends WordSpec with Matchers {

  def ext = Map("foo" -> "bar")

  "MediaRanges" should {

    "Perform equality correctly" in {
      `text/*` should equal(`text/*`)

      `text/*`.withQuality(0.4) should equal(`text/*`.withQuality(0.4))
      `text/*`.withQuality(0.4).withExtensions(ext) should equal(`text/*`.withQuality(0.4).withExtensions(ext))
      `text/*`.withQuality(0.4).withExtensions(ext) should not equal(`text/*`.withQuality(0.4))

      `text/*`.withQuality(0.1) should not equal(`text/*`.withQuality(0.4))
      `text/*`.withQuality(0.4) should not equal(`text/*`.withQuality(0.1))

      `text/*` should not equal(`audio/*`)
    }

    "Be satisfiedBy MediaRanges correctly" in {
      `text/*`.withQuality(0.5).satisfiedBy(`text/*`.withQuality(0.4)) should equal(false)
      `text/*`.withQuality(0.3).satisfiedBy(`text/*`.withQuality(0.4)) should equal(true)

      `text/*`.satisfiedBy(`image/*`) should equal(false)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/*`.satisfiedBy(`text/css`) should equal(true)
      `text/*`.satisfiedBy(`text/css`.withQuality(0.2)) should equal(false)
      `text/*`.withQuality(0.2).satisfiedBy(`text/css`) should equal(true)
      `text/*`.satisfiedBy(`audio/aiff`) should equal(false)
    }

    "be satisfied regardless of extensions" in {
      `text/*`.withExtensions(ext).satisfies(`text/*`) should equal(true)
      `text/*`.withExtensions(ext).satisfies(`text/*`) should equal(true)
    }
  }

  "MediaTypes" should {

    "Perform equality correctly" in {
      `text/html` should equal(`text/html`)
      `text/html`.withQuality(0.4) should equal(`text/html`.withQuality(0.4))

      `text/html`.withQuality(0.4).withExtensions(ext) should not equal(`text/html`.withQuality(0.4))
      `text/html`.withQuality(0.4) should not equal(`text/html`.withQuality(0.4).withExtensions(ext))


      `text/html`.withQuality(0.1) should not equal(`text/html`.withQuality(0.4))
      `text/html`.withQuality(0.4) should not equal(`text/html`.withQuality(0.1))

      `text/html` should not equal(`text/css`)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/html`.satisfiedBy(`text/css`) should equal(false)
      `text/html`.withQuality(0.5).satisfiedBy(`text/html`.withQuality(0.4)) should equal(false)
      `text/html`.withQuality(0.3).satisfiedBy(`text/html`.withQuality(0.4)) should equal(true)

      `text/html`.satisfies(`text/css`) should equal(false)
      `text/html`.withQuality(0.5).satisfies(`text/html`.withQuality(0.4)) should equal(true)
      `text/html`.withQuality(0.3).satisfies(`text/html`.withQuality(0.4)) should equal(false)
    }

    "Not be satisfied by MediaRanges" in {
      `text/html`.satisfiedBy(`text/*`) should equal(false)
    }

    "Satisfy MediaRanges" in {
      `text/html`.satisfies(`text/*`) should equal(true)
      `text/*`.satisfies(`text/html`) should equal(false)
    }

    "be satisfied regardless of extensions" in {
      `text/html`.withExtensions(ext).satisfies(`text/*`) should equal(true)
      `text/*`.satisfies(`text/html`.withExtensions(ext)) should equal(false)

      `text/html`.satisfies(`text/*`.withExtensions(ext)) should equal(true)
      `text/*`.withExtensions(ext).satisfies(`text/html`) should equal(false)
    }
  }

  "MediaRanges and MediaTyes" should {
    "Do inequality amongst each other properly" in {
      val r = `text/*`
      val t = `text/asp`

      r should not equal(t)
      t should not equal(r)

      r.withQuality(0.1) should not equal(t.withQuality(0.1))
      t.withQuality(0.1) should not equal(r.withQuality(0.1))

      r.withExtensions(ext) should not equal(t.withExtensions(ext))
      t.withExtensions(ext) should not equal(r.withExtensions(ext))
    }

    "Not accept illegal q values" in {
      an [IllegalArgumentException] should be thrownBy `text/*`.withQuality(2.0)
      an [IllegalArgumentException] should be thrownBy `text/*`.withQuality(-2.0)

      an [IllegalArgumentException] should be thrownBy `text/html`.withQuality(2.0)
      an [IllegalArgumentException] should be thrownBy `text/html`.withQuality(-2.0)
    }
  }
}
