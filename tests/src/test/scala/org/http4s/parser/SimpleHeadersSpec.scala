package org.http4s
package parser

import java.time.Instant

import Http4s._
import headers._
import org.http4s.headers.ETag.EntityTag
import scalaz.{\/-, Success}
import org.http4s.util.NonEmptyList
import java.net.{InetAddress, Inet6Address}

class SimpleHeadersSpec extends Http4sSpec {

  "SimpleHeaders" should {

    "parse Connection" in {
      val header = Connection("closed".ci)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse Content-Length" in {
      val header = `Content-Length`.unsafeFromLong(4)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name, fv"foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Content-Encoding" in {
      val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
    }

    "parse Content-Disposition" in {
      val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name, fv"foo; bar")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Date" in {       // mills are lost, get rid of them
      val header = Date(Instant.now).toRaw.parsed
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name, fv"foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Host" in {
      val header1 = Host("foo", Some(5))
      HttpHeaderParser.parseHeader(header1.toRaw) must be_\/-(header1)

      val header2 = Host("foo", None)
      HttpHeaderParser.parseHeader(header2.toRaw) must be_\/-(header2)

      val bad = Header(header1.name, fv"foo:bar")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse Last-Modified" in {
      val header = `Last-Modified`(Instant.now).toRaw.parsed
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name, fv"foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse If-Modified-Since" in {
      val header = `If-Modified-Since`(Instant.now).toRaw.parsed
      HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)

      val bad = Header(header.name, fv"foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/
    }

    "parse ETag" in {
      ETag.EntityTag("hash", weak = true).toString() must_== "W/\"hash\""
      ETag.EntityTag("hash", weak = false).toString() must_== "\"hash\""

      val headers = Seq(ETag("hash"),
                        ETag("hash", true))

      foreach(headers){ header =>
        HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
      }
    }

    "parse If-None-Match" in {
      val headers = Seq(`If-None-Match`(EntityTag("hash")),
                        `If-None-Match`(EntityTag("123-999")),
                        `If-None-Match`(EntityTag("123-999"), EntityTag("hash")),
                        `If-None-Match`(EntityTag("123-999", weak = true), EntityTag("hash")),
                        `If-None-Match`.`*`)
      foreach(headers){ header =>
        HttpHeaderParser.parseHeader(header.toRaw) must be_\/-(header)
      }
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

      val headerstr = fv"Mozilla/5.0 (Android; Mobile; rv:30.0) Gecko/30.0 Firefox/30.0"
      HttpHeaderParser.parseHeader(Header.Raw(`User-Agent`.name, headerstr)) must be_\/-(
        `User-Agent`(AgentProduct("Mozilla", Some("5.0")), Seq(
            AgentComment("Android; Mobile; rv:30.0"),
            AgentProduct("Gecko", Some("30.0")),
            AgentProduct("Firefox", Some("30.0"))
          )
        )
      )
    }

    "parse X-Forwarded-For" in {
      // ipv4
      val header2 = `X-Forwarded-For`(NonEmptyList(
        Some(InetAddress.getLocalHost),
        Some(InetAddress.getLoopbackAddress)))
      HttpHeaderParser.parseHeader(header2.toRaw) must be_\/-(header2)

      // ipv6
      val header3 = `X-Forwarded-For`(NonEmptyList(
        Some(InetAddress.getByName("::1")),
        Some(InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7334"))))
      HttpHeaderParser.parseHeader(header3.toRaw) must be_\/-(header3)

      // "unknown"
      val header4 = `X-Forwarded-For`(NonEmptyList(None))
      HttpHeaderParser.parseHeader(header4.toRaw) must be_\/-(header4)

      val bad = Header(fn"x-forwarded-for", fv"foo")
      HttpHeaderParser.parseHeader(bad) must be_-\/

      val bad2 = Header(fn"x-forwarded-for", fv"256.56.56.56")
      HttpHeaderParser.parseHeader(bad2) must be_-\/
    }
  }
}
