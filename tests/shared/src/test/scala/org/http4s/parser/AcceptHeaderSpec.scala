package org.http4s
package parser

import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s.MediaRange._
import org.http4s.MediaType._
import org.specs2.mutable.Specification

class AcceptHeaderSpec extends Specification with HeaderParserHelper[Accept] with Http4s {

  def hparse(value: String): ParseResult[Accept] = HttpHeaderParser.ACCEPT(value)

  def ext = Map("foo" -> "bar", "baz" -> "whatever")

  "Accept-Header parser" should {

    "Parse all registered MediaRanges" in {
      // Parse a single one
      parse("image/*").values.head must be_==~(`image/*`)

      // Parse the rest
      foreach(MediaRange.snapshot.values) { m =>
        val r = parse(m.renderString).values.head
        r must be_===(MediaRangeAndQValue(m))
      }
    }

    "Deal with '.' and '+' chars" in {
      val value = "application/soap+xml, application/vnd.ms-fontobject"
      val accept = Accept(`application/soap+xml`, `application/vnd.ms-fontobject`)
      parse(value) must be_===(accept)

    }

    "Parse all registered MediaTypes" in {
      // Parse a single one
      parse("image/jpeg").values.head must be_==~(`image/jpeg`)

      // Parse the rest
      foreach(MediaType.snapshot.values) { m =>
        val r = parse(m.renderString).values.head
        r must be_===(MediaRangeAndQValue(m))
      }
    }

    "Parse multiple Ranges" in {
      // Just do a single type
      val accept = Accept(`audio/*`, `video/*`)
      parse(accept.value) must be_===(accept)

      val accept2 = Accept(`audio/*`.withQValue(q(0.2)), `video/*`)
      parse(accept2.value) must be_===(accept2)

      // Go through all of them
      {
        val samples = MediaRange.snapshot.values.map(MediaRangeAndQValue(_))
        foreach(samples.sliding(4).toArray) { sample =>
          val h = Accept(sample.head, sample.tail.toSeq: _*)
          parse(h.value) must be_===(h)
        }
      }

      // Go through all of them with q and extensions
      {
        val samples = MediaRange.snapshot.values.map(_.withExtensions(ext).withQValue(q(0.2)))
        foreach(samples.sliding(4).toArray) { sample =>
          val h = Accept(sample.head, sample.tail.toSeq: _*)
          parse(h.value) must be_===(h)
        }
      }

    }

    "Parse multiple Types" in {
      // Just do a single type
      val accept = Accept(`audio/mod`, `audio/mpeg`)
      parse(accept.value) must be_===(accept)

      // Go through all of them
      val samples = MediaType.snapshot.values.map(MediaRangeAndQValue(_))
      foreach(samples.sliding(4).toArray) { sample =>
        val h = Accept(sample.head, sample.tail.toSeq: _*)
        parse(h.value) must be_===(h)
      }
    }

    "Deal with q and extensions" in {
      val value = "text/*;q=0.3, text/html;q=0.7, text/html;level=1"
      parse(value) must be_===(
        Accept(
          `text/*`.withQValue(q(0.3)),
          `text/html`.withQValue(q(0.7)),
          `text/html`.withExtensions(Map("level" -> "1"))
        ))

      // Go through all of them
      val samples = MediaType.snapshot.values.map(_.withExtensions(ext).withQValue(q(0.2)))
      foreach(samples.sliding(4).toArray) { sample =>
        val h = Accept(sample.head, sample.tail.toSeq: _*)
        parse(h.value) must be_===(h)
      }
    }
  }

}
