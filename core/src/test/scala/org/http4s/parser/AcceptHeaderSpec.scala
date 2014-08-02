package org.http4s.parser

import org.http4s.Header.Accept
import org.http4s.{MediaType, MediaRange}
import MediaRange._
import MediaType._
import org.specs2.mutable.Specification
import scalaz.Validation

class AcceptHeaderSpec extends Specification with HeaderParserHelper[Accept] {


  def hparse(value: String): Validation[ParseErrorInfo, Accept] = HttpParser.ACCEPT(value)

  def ext = Map("foo" -> "bar", "baz" -> "whatever")


  "Accept-Header parser" should {

    "Parse all registered MediaRanges" in {
      // Parse a single one
      parse("image/*").values.head must be_==(`image/*`)

      // Parse the rest
      foreach(MediaRange.snapshot.values) { m =>
        val r = parse(m.value).values.head
        r must be_==(m)           // Structural equality
        (r eq m) must be_==(true) // Reference equality
      }
    }

    "Deal with '.' and '+' chars" in {
      val value = "application/soap+xml, application/vnd.ms-fontobject"
      val accept = Accept(`application/soap+xml`, `application/vnd.ms-fontobject`)
      parse(value) must be_==(accept)

    }

    "Parse all registered MediaTypes" in {
      // Parse a single one
      parse("image/jpeg").values.head must be_==(`image/jpeg`)

      // Parse the rest
      foreach(MediaType.snapshot.values) { m =>
        val r = parse(m.value).values.head
        r must be_==(m)           // Structural equality
        (r eq m) must be_==(true) // Reference equality
      }
    }

    "Parse multiple Ranges" in {
      // Just do a single type
      val accept = Accept(`audio/*`, `video/*`)
      parse(accept.value) must be_==(accept)

      val accept2 = Accept(`audio/*`.withQValue(0.2), `video/*`)
      parse(accept2.value) must be_==(accept2)


      // Go through all of them
      {
        val ranges = MediaRange.snapshot.values.toArray
        foreach(0 until (ranges.length-1)) { i =>
          val subrange = ranges.slice(i, i + 4)
          val h = Accept(subrange.head, subrange.tail:_*)
          parse(h.value) must be_==(h)
        }
      }

      // Go through all of them with q and extensions
      {
        val ranges = MediaRange.snapshot.values.toArray
        foreach(0 until (ranges.length-1)) { i =>
          val subrange = ranges.slice(i, i + 4).map(_.withQValue(0.2f).withExtensions(ext))
          val h = Accept(subrange.head, subrange.tail:_*)
          parse(h.value) must be_==(h)
        }
      }

    }

    "Parse multiple Types" in {
      // Just do a single type
      val accept = Accept(`audio/mod`, `audio/mpeg`)
      parse(accept.value) must be_==(accept)

      // Go through all of them
      val ranges = MediaType.snapshot.values.toArray
      foreach(0 until (ranges.length-1)) { i =>
        val subrange = ranges.slice(i, i + 4)
        val h = Accept(subrange.head, subrange.tail:_*)
        parse(h.value) must be_==(h)
      }
    }

    "Deal with q and extensions" in {
      val value = "text/*;q=0.3, text/html;q=0.7, text/html;level=1"
      parse(value) must be_==(Accept(
        `text/*`.withQValue(0.3),
        `text/html`.withQValue(0.7),
        `text/html`.withExtensions(Map("level" -> "1"))
      ))

      // Go through all of them
      val ranges = MediaType.snapshot.values.toArray
      foreach(0 until (ranges.length-1)) { i =>
        val subrange = ranges.slice(i, i + 4).map(_.withQValue(0.2f).withExtensions(ext))
        val h = Accept(subrange.head, subrange.tail:_*)
        parse(h.value) must be_==(h)
      }
    }
  }

}
