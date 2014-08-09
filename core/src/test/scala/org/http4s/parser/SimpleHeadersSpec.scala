package org.http4s
package parser

import Http4s._
import Header._
import org.specs2.mutable.Specification
import scalaz.{NonEmptyList, Success}
import java.net.InetAddress

class SimpleHeadersSpec extends Http4sSpec {

  "SimpleHeaders" should {

    "parse Connection" in {
      val header = Connection("closed".ci)
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)
    }

    "parse Content-Length" in {
      val header = `Content-Length`(4)
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }

    "parse Content-Encoding" in {
      val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)
    }

    "parse Content-Disposition" in {
      val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)

      val bad = Header(header.name.toString, "foo; bar")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }

    "parse Date" in {       // mills are lost, get rid of them
      val header = Date(DateTime.now).toRaw.parsed
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }

    "parse Host" in {
      val header1 = Host("foo", Some(5))
      HttpParser.parseHeader(header1.toRaw) must beRightDisjunction(header1)

      val header2 = Host("foo", None)
      HttpParser.parseHeader(header2.toRaw) must beRightDisjunction(header2)

      val bad = Header(header1.name.toString, "foo:bar")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }

    "parse Last-Modified" in {
      val header = `Last-Modified`(DateTime.now).toRaw.parsed
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }

    "parse If-Modified-Since" in {
      val header = `If-Modified-Since`(DateTime.now).toRaw.parsed
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }

    "parse ETag" in {
      val header = ETag("hash")
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)
    }

    "parse If-None-Match" in {
      val header = `If-None-Match`("hash")
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)
    }

    "parse Transfer-Encoding" in {
      val header = `Transfer-Encoding`(TransferCoding.chunked)
      HttpParser.parseHeader(header.toRaw) must beRightDisjunction(header)

      val header2 = `Transfer-Encoding`(TransferCoding.compress)
      HttpParser.parseHeader(header2.toRaw) must beRightDisjunction(header2)
    }

    "parse X-Forward-Spec" in {
      val header1 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost)))
      HttpParser.parseHeader(header1.toRaw) must beRightDisjunction(header1)

      val header2 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost),
                                Some(InetAddress.getLoopbackAddress)))
      HttpParser.parseHeader(header2.toRaw) must beRightDisjunction(header2)

      val bad = Header(header1.name.toString, "foo")
      HttpParser.parseHeader(bad) must beLeftDisjunction
    }
  }

}
