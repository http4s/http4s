/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package parser

import cats.data.NonEmptyList
import com.comcast.ip4s._
import org.http4s.headers._
import org.http4s.EntityTag.{Strong, Weak}
import org.typelevel.ci.CIString


class SimpleHeadersSpec extends Http4sSuite {
  test("parse Accept-Patch") {
    def parse(value: String) = `Accept-Patch`.parse(value)

    val header =
      `Accept-Patch`(
        NonEmptyList.of(new MediaType("text", "example", extensions = Map("charset" -> "utf-8"))))
    assertEquals(parse(header.value), Right(header))
    val multipleMediaTypes =
      `Accept-Patch`(
        NonEmptyList
          .of(new MediaType("application", "example"), new MediaType("text", "example")))
    assertEquals(parse(multipleMediaTypes.value), Right(multipleMediaTypes))

    val bad = Header("Accept-Patch", "foo; bar")
    assert(parse(bad.value).isLeft)
  }

  test("parse Access-Control-Allow-Headers") {
    val header = `Access-Control-Allow-Headers`(
      NonEmptyList.of(
        CIString("Accept"),
        CIString("Expires"),
        CIString("X-Custom-Header"),
        CIString("*")
      )
    )
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val invalidHeader = Header(header.name.toString, "(non-token-name), non[&token]name")
    assert(HttpHeaderParser.parseHeader(invalidHeader).isLeft)
  }

  test("parse Access-Control-Expose-Headers") {
    val header = `Access-Control-Expose-Headers`(
      NonEmptyList.of(
        CIString("Content-Length"),
        CIString("Authorization"),
        CIString("X-Custom-Header"),
        CIString("*")
      )
    )
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val invalidHeader = Header(header.name.toString, "(non-token-name), non[&token]name")
    assert(HttpHeaderParser.parseHeader(invalidHeader).isLeft)
  }

  test("parse Connection") {
    val header = Connection(CIString("closed"))
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))
  }

  test("SimpleHeaders should parse Content-Length") {
    val header = `Content-Length`.unsafeFromLong(4)
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "foo")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("SimpleHeaders should parse Content-Encoding") {
    val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))
  }

  test("SimpleHeaders should parse Content-Disposition") {
    val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
    val parse = `Content-Disposition`.parse(_)
    assertEquals(parse(header.value), Right(header))

    assert(parse("foo; bar").isLeft)
  }

  test("SimpleHeaders should parse Date") { // mills are lost, get rid of them
    val header = Date(HttpDate.Epoch).toRaw.parsed
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "foo")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("SimpleHeaders should parse Host") {
    val header1 = headers.Host("foo", Some(5))
    assertEquals(HttpHeaderParser.parseHeader(header1.toRaw), Right(header1))

    val header2 = headers.Host("foo", None)
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    val bad = Header(header1.name.toString, "foo:bar")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("parse Access-Control-Allow-Credentials") {
    val header = `Access-Control-Allow-Credentials`().toRaw.parsed
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "false")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)

    // it is case sensitive
    val bad2 = Header(header.name.toString, "True")
    assert(HttpHeaderParser.parseHeader(bad2).isLeft)
  }

  test("SimpleHeaders should parse Last-Modified") {
    val header = `Last-Modified`(HttpDate.Epoch).toRaw.parsed
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "foo")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("SimpleHeaders should parse If-Modified-Since") {
    val header = `If-Modified-Since`(HttpDate.Epoch).toRaw.parsed
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "foo")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("SimpleHeaders should parse ETag") {
    assertEquals(ETag.EntityTag("hash", Weak).toString(), "W/\"hash\"")
    assertEquals(ETag.EntityTag("hash", Strong).toString(), "\"hash\"")

    val headers = Seq(ETag("hash"), ETag("hash", Weak))

    headers.foreach { header =>
      assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))
    }
  }

  test("SimpleHeaders should parse If-None-Match") {
    val headers = Seq(
      `If-None-Match`(EntityTag("hash")),
      `If-None-Match`(EntityTag("123-999")),
      `If-None-Match`(EntityTag("123-999"), EntityTag("hash")),
      `If-None-Match`(EntityTag("123-999", Weak), EntityTag("hash")),
      `If-None-Match`.`*`
    )
    headers.foreach { header =>
      assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))
    }
  }

  test("parse Max-Forwards") {
    val headers = Seq(
      `Max-Forwards`.unsafeFromLong(0),
      `Max-Forwards`.unsafeFromLong(100)
    )
    headers.foreach { header =>
      assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))
    }
  }

  test("SimpleHeaders should parse Transfer-Encoding") {
    val header = `Transfer-Encoding`(TransferCoding.chunked)
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val header2 = `Transfer-Encoding`(TransferCoding.compress)
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))
  }

  test("SimpleHeaders should parse User-Agent") {
    val header = `User-Agent`(ProductId("foo", Some("bar")), List(ProductId("foo")))
    assertEquals(header.value, "foo/bar foo")

    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val header2 =
      `User-Agent`(ProductId("foo"), List(ProductId("bar", Some("biz")), ProductComment("blah")))
    assertEquals(header2.value, "foo bar/biz (blah)")
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    val headerstr = "Mozilla/5.0 (Android; Mobile; rv:30.0) Gecko/30.0 Firefox/30.0"
    val parsed = HttpHeaderParser.parseHeader(Header.Raw(`User-Agent`.name, headerstr))
    assertEquals(
      parsed,
      Right(
        `User-Agent`(
          ProductId("Mozilla", Some("5.0")),
          List(
            ProductComment("Android; Mobile; rv:30.0"),
            ProductId("Gecko", Some("30.0")),
            ProductId("Firefox", Some("30.0"))
          )
        )
      )
    )
    assertEquals(parsed.map(_.value), Right(headerstr))
  }

  test("parse Server") {
    val header = Server(ProductId("foo", Some("bar")), List(ProductComment("foo")))
    assertEquals(header.value, "foo/bar (foo)")

    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val header2 =
      Server(ProductId("foo"), List(ProductId("bar", Some("biz")), ProductComment("blah")))
    assertEquals(header2.value, "foo bar/biz (blah)")
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    val headerstr = "nginx/1.14.0 (Ubuntu)"
    assertEquals(
      HttpHeaderParser.parseHeader(Header.Raw(Server.name, headerstr)),
      Right(
        Server(
          ProductId("nginx", Some("1.14.0")),
          List(
            ProductComment("Ubuntu")
          )
        )
      ))

    val headerstr2 = "CERN/3.0 libwww/2.17"
    assertEquals(
      HttpHeaderParser.parseHeader(Header.Raw(Server.name, headerstr2)),
      Right(
        Server(
          ProductId("CERN", Some("3.0")),
          List(
            ProductId("libwww", Some("2.17"))
          )
        )
      )
    )
  }

  test("SimpleHeaders should parse X-Forwarded-For") {
    // ipv4
    val header2 = `X-Forwarded-For`(NonEmptyList.of(Some(ipv4"127.0.0.1")))
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    // ipv6
    val header3 = `X-Forwarded-For`(
      NonEmptyList.of(Some(ipv6"::1"), Some(ipv6"2001:0db8:85a3:0000:0000:8a2e:0370:7334")))
    assertEquals(HttpHeaderParser.parseHeader(header3.toRaw), Right(header3))

    // "unknown"
    val header4 = `X-Forwarded-For`(NonEmptyList.of(None))
    assertEquals(HttpHeaderParser.parseHeader(header4.toRaw), Right(header4))

    val bad = Header("x-forwarded-for", "foo")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)

    val bad2 = Header("x-forwarded-for", "256.56.56.56")
    assert(HttpHeaderParser.parseHeader(bad2).isLeft)
  }

}
