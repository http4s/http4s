package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}

import org.http4s.Header.Accept
import org.http4s.{MediaType, MediaRange}
import MediaRange._
import MediaType._

/**
 * @author Bryce Anderson
 *         Created on 12/23/13
 */
class AcceptHeaderSpec extends WordSpec with Matchers {

  def ext = Map("foo" -> "bar")

  // Also checks to make sure whitespace doesn't effect the outcome
  private def parse(value: String): Accept = {
    val a = HttpParser.ACCEPT(value).fold(err => sys.error(err.detail), identity)
    val b = HttpParser.ACCEPT(value.replace(" ", "")).fold(err => sys.error(err.detail), identity)
    assert(a == b, "Whitespace resulted in different Accept headers")
    a
  }

  "Accept-Header parser" should {

    "Parse all registered MediaRanges" in {
      // Parse a single one
      parse("image/*").values.head should equal(`image/*`)

      // Parse the rest
      MediaRange.snapshot.values.foreach { m =>
        parse(m.value).values.head should equal(m)
      }
    }

    "Deal with '.' and '+' chars" in {
      val value = "application/soap+xml, application/vnd.ms-fontobject"
      val accept = Accept(`application/soap+xml`, `application/vnd.ms-fontobject`)
      parse(value) should equal(accept)

    }

    "Parse all registered MediaTypes" in {
      // Parse a single one
      parse("image/jpeg").values.head should equal(`image/jpeg`)

      // Parse the rest
      MediaType.snapshot.values.foreach { m =>
        parse(m.value).values.head should equal(m)
      }
    }

    "Parse multiple Ranges" in {
      // Just do a single type
      val accept = Accept(`audio/*`, `video/*`)
      parse(accept.value) should equal(accept)

      val accept2 = Accept(`audio/*`.withq(0.2f), `video/*`)
      parse(accept2.value) should equal(accept2)


      // Go through all of them
      {
        val ranges = MediaRange.snapshot.values.toArray
        0 until (ranges.length-1) foreach { i =>
          val subrange = ranges.slice(i, i + 4)
          val h = Accept(subrange.head, subrange.tail:_*)
          parse(h.value) should equal(h)
        }
      }

      // Go through all of them with q and extensions
      {
        val ranges = MediaRange.snapshot.values.toArray
        0 until (ranges.length-1) foreach { i =>
          val subrange = ranges.slice(i, i + 4).map(_.withqextensions(0.2f, ext))
          val h = Accept(subrange.head, subrange.tail:_*)
          parse(h.value) should equal(h)
        }
      }

    }

    "Parse multiple Types" in {
      // Just do a single type
      val accept = Accept(`audio/mod`, `audio/mpeg`)
      parse(accept.value) should equal(accept)

      // Go through all of them
      val ranges = MediaType.snapshot.values.toArray
      0 until (ranges.length-1) foreach { i =>
        val subrange = ranges.slice(i, i + 4)
        val h = Accept(subrange.head, subrange.tail:_*)
        parse(h.value) should equal(h)
      }
    }

    // TODO: Don't ignore q and extensions!
    "Deal with q and extensions TODO: allow implementation of them!" in {
      val value = "text/*;q=0.3, text/html;q=0.7, text/html;level=1"
      parse(value) should equal(Accept(
        `text/*`.withq(0.3f),
        `text/html`.withq(0.7f),
        `text/html`.withextensions(Map("level" -> "1"))
      ))

      // Go through all of them
      val ranges = MediaType.snapshot.values.toArray
      0 until (ranges.length-1) foreach { i =>
        val subrange = ranges.slice(i, i + 4).map(_.withqextensions(0.2f, ext))
        val h = Accept(subrange.head, subrange.tail:_*)
        parse(h.value) should equal(h)
      }
    }
  }

}
