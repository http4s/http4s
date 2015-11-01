package org.http4s
package parser

import Http4s._
import headers._
import org.specs2.mutable.Specification
import scalaz.{NonEmptyList, Success}
import java.net.InetAddress

class SimpleHeadersSpec extends Http4sSpec {

  "SimpleHeaders" should {

    "parse Allow" in {
      val header = Allow(Method.GET, Method.POST)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse Connection" in {
      val header = Connection("closed".ci)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse Content-Length" in {
      val header = `Content-Length`(4)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name.toString, "foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Content-Encoding" in {
      val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse Content-Disposition" in {
      val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name.toString, "foo; bar")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Date" in {       // mills are lost, get rid of them
      val header = Date(DateTime.now).toRaw.parsed
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name.toString, "foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Host" in {
      val header1 = Host("foo", Some(5))
      HttpHeaderParser.parseHeader(header1.toRaw) must be_\/-(header1)

      val header2 = Host("foo", None)
      HttpHeaderParser.parseHeader(header2.toRaw) must be_\/-(header2)

      val bad = Header(header1.name.toString, "foo:bar")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Last-Modified" in {
      val header = `Last-Modified`(DateTime.now).toRaw.parsed
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name.toString, "foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse If-Modified-Since" in {
      val header = `If-Modified-Since`(DateTime.now).toRaw.parsed
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name.toString, "foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse ETag" in {
      val header = ETag("hash")
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse If-None-Match" in {
      val header = `If-None-Match`("hash")
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse Transfer-Encoding" in {
      val header = `Transfer-Encoding`(TransferCoding.chunked)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val header2 = `Transfer-Encoding`(TransferCoding.compress)
      HttpHeaderParser.parseHeader(header2.toRaw) must be_\/-(header2)
    }

    "parse User-Agent" in {
      val header = `User-Agent`(AgentProduct("foo", Some("bar")), Seq(AgentComment("foo")))
      header.value must_== "foo/bar (foo)"

      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val header2 = `User-Agent`(AgentProduct("foo"), Seq(AgentProduct("bar", Some("biz")), AgentComment("blah")))
      header2.value must_== "foo bar/biz (blah)"
      HttpHeaderParser.parseHeader(header2.toRaw) must be_\/-(header2)

      val headerstr = "Mozilla/5.0 (Android; Mobile; rv:30.0) Gecko/30.0 Firefox/30.0"
      HttpHeaderParser.parseHeader(Header.Raw(`User-Agent`.name, headerstr)) must be_\/-(
        `User-Agent`(AgentProduct("Mozilla", Some("5.0")), Seq(
            AgentComment("Android; Mobile; rv:30.0"),
            AgentProduct("Gecko", Some("30.0")),
            AgentProduct("Firefox", Some("30.0"))
          )
        )
      )
    }

    "parse X-Forward-Spec" in {
      val header1 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost)))
      HttpHeaderParser.parseHeader(header1.toRaw) must be_\/-(header1)

      val header2 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost),
                                Some(InetAddress.getLoopbackAddress)))
      HttpHeaderParser.parseHeader(header2.toRaw) must be_\/-(header2)

      val bad = Header(header1.name.toString, "foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }
  }

}
