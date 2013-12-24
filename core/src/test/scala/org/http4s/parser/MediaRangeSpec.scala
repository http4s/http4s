package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.MediaRange._
import org.http4s.MediaType._

/**
 * @author Bryce Anderson
 *         Created on 12/24/13
 */
class MediaRangeSpec extends WordSpec with Matchers {

  def ext = Map("foo" -> "bar")

  "MediaRanges" should {

    "Perform equality correctly" in {
      `text/*` should equal(`text/*`)

      `text/*`.withq(0.4f) should equal(`text/*`.withq(0.4f))
      `text/*`.withqextensions(0.4f, ext) should equal(`text/*`.withqextensions(0.4f, ext))
      `text/*`.withqextensions(0.4f, ext) should not equal(`text/*`.withq(0.4f))

      `text/*`.withq(0.1f) should not equal(`text/*`.withq(0.4f))
      `text/*`.withq(0.4f) should not equal(`text/*`.withq(0.1f))

      `text/*` should not equal(`audio/*`)
    }

    "Be satisfiedBy MediaRanges correctly" in {
      `text/*`.withq(0.5f).satisfiedBy(`text/*`.withq(0.4f)) should equal(true)
      `text/*`.withq(0.3f).satisfiedBy(`text/*`.withq(0.4f)) should equal(false)

      `text/*`.satisfiedBy(`image/*`) should equal(false)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/*`.satisfiedBy(`text/css`) should equal(true)
      `text/*`.satisfiedBy(`text/css`.withq(0.2f)) should equal(true)
      `text/*`.withq(0.2f).satisfiedBy(`text/css`) should equal(false)
      `text/*`.satisfiedBy(`audio/aiff`) should equal(false)
    }
  }

  "MediaTypes" should {

    "Perform equality correctly" in {
      `text/html` should equal(`text/html`)
      `text/html`.withq(0.4f) should equal(`text/html`.withq(0.4f))

      `text/html`.withqextensions(0.4f, ext) should not equal(`text/html`.withq(0.4f))
      `text/html`.withq(0.4f) should not equal(`text/html`.withqextensions(0.4f, ext))


      `text/html`.withq(0.1f) should not equal(`text/html`.withq(0.4f))
      `text/html`.withq(0.4f) should not equal(`text/html`.withq(0.1f))

      `text/html` should not equal(`text/css`)
    }

    "Be satisfiedBy MediaTypes correctly" in {
      `text/html`.satisfiedBy(`text/css`) should equal(false)
      `text/html`.withq(0.5f).satisfiedBy(`text/html`.withq(0.4f)) should equal(true)
      `text/html`.withq(0.3f).satisfiedBy(`text/html`.withq(0.4f)) should equal(false)
    }

    "Not be satisfied by MediaRanges" in {
      `text/html`.satisfiedBy(`text/*`) should equal(false)
    }
  }
}
