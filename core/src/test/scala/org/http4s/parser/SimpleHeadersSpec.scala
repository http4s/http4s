package org.http4s
package parser

import org.scalatest.{Matchers, WordSpec}
import Header._
import scalaz.{NonEmptyList, Success}
import org.joda.time.DateTime
import java.net.InetAddress

/**
 * @author Bryce Anderson
 *         Created on 12/22/13
 */
class SimpleHeadersSpec extends WordSpec with Matchers {

  "SimpleHeaders" should {

    "parse Connection" in {
      val header = Connection("closed")
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))
    }

    "parse Content-Length" in {
      val header = `Content-Length`(4)
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "parse Content-Encoding" in {
      val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))
    }

    "parse Content-Disposition" in {
      val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo; bar")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "parse Date" in {       // mills are lost, get rid of them
      val header = Date(new DateTime()).toRaw.parsed
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "parse Host" in {
      val header1 = Host("foo", Some(5))
      HttpParser.parseHeader(header1.toRaw) should equal(Success(header1))

      val header2 = Host("foo", None)
      HttpParser.parseHeader(header2.toRaw) should equal(Success(header2))

      val bad = Header(header1.name.toString, "foo:bar")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "parse Last-Modified" in {
      val header = `Last-Modified`(new DateTime()).toRaw.parsed
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "parse If-Modified-Since" in {
      val header = `If-Modified-Since`(new DateTime()).toRaw.parsed
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "parse ETag" in {
      val header = ETag("hash")
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))
    }

    "parse If-None-Match" in {
      val header = `If-None-Match`("hash")
      HttpParser.parseHeader(header.toRaw) should equal(Success(header))
    }

    "parse X-Forward-Spec" in {
      val header1 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost)))
      HttpParser.parseHeader(header1.toRaw) should equal(Success(header1))

      val header2 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost),
                                Some(InetAddress.getLoopbackAddress)))
      HttpParser.parseHeader(header2.toRaw) should equal(Success(header2))

      val bad = Header(header1.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }
  }

}
