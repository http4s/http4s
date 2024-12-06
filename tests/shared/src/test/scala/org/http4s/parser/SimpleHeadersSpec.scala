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
import org.http4s.EntityTag.Strong
import org.http4s.EntityTag.Weak
import org.http4s.headers._
import org.http4s.syntax.header._
import org.typelevel.ci._

class SimpleHeadersSpec extends Http4sSuite {
  test("parse Accept-Patch") {
    def parse(value: String) = `Accept-Patch`.parse(value)

    val header =
      `Accept-Patch`(
        NonEmptyList.of(new MediaType("text", "example", extensions = Map("charset" -> "utf-8")))
      )
    assertEquals(parse(header.value), Right(header))
    val multipleMediaTypes =
      `Accept-Patch`(
        NonEmptyList
          .of(new MediaType("application", "example"), new MediaType("text", "example"))
      )
    assertEquals(parse(multipleMediaTypes.value), Right(multipleMediaTypes))

    assert(parse("foo; bar").isLeft)
  }

  test("parse Access-Control-Allow-Headers") {

    val header = `Access-Control-Allow-Headers`(
      ci"Accept",
      ci"Expires",
      ci"X-Custom-Header",
      ci"*",
    )

    assertEquals(`Access-Control-Allow-Headers`.parse(header.toRaw1.value), Right(header))

    val invalidHeaderValue = "(non-token-name), non[&token]name"
    assert(`Access-Control-Allow-Headers`.parse(invalidHeaderValue).isLeft)

    assertEquals(
      `Access-Control-Allow-Headers`.parse(""),
      Right(`Access-Control-Allow-Headers`.empty),
    )
  }

  test("parse Access-Control-Expose-Headers") {
    val header = `Access-Control-Expose-Headers`(
      ci"Content-Length",
      ci"Authorization",
      ci"X-Custom-Header",
      ci"*",
    )
    assertEquals(`Access-Control-Expose-Headers`.parse(header.toRaw1.value), Right(header))

    val invalidHeaderValue = "(non-token-name), non[&token]name"
    assert(`Access-Control-Expose-Headers`.parse(invalidHeaderValue).isLeft)

    assertEquals(
      `Access-Control-Expose-Headers`.parse(""),
      Right(`Access-Control-Expose-Headers`.empty),
    )
  }

  test("parse Connection") {
    val header = Connection(ci"closed")
    assertEquals(Connection.parse(header.toRaw1.value), Right(header))
  }

  test("Parse Content-Length") {
    assertEquals(`Content-Length`.parse("4"), Right(`Content-Length`.unsafeFromLong(4)))
    assert(`Content-Length`.parse("foo").isLeft)
  }

  test("SimpleHeaders should parse Content-Encoding") {
    val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
    assertEquals(`Content-Encoding`.parse(header.value), Right(header))
  }

  test("SimpleHeaders should parse Content-Disposition") {
    val header = `Content-Disposition`("foo", Map(ci"one" -> "two", ci"three" -> "four"))
    val parse = `Content-Disposition`.parse(_)
    assertEquals(parse(header.value), Right(header))

    assert(parse("foo; bar").isLeft)
  }

  test("SimpleHeaders should parse Date") { // mills are lost, get rid of them
    val header = Date(HttpDate.Epoch)
    val stringRepr = "Thu, 01 Jan 1970 00:00:00 GMT"
    assertEquals(Date(HttpDate.Epoch).value, stringRepr)
    assertEquals(Date.parse(stringRepr), Right(header))

    assert(Date.parse("foo").isLeft)
  }

  test("SimpleHeaders should parse Host") {
    val header1 = headers.Host("foo", Some(5))
    assertEquals(headers.Host.parse("foo:5"), Right(header1))

    val header2 = headers.Host("foo", None)
    assertEquals(headers.Host.parse("foo"), Right(header2))

    assert(headers.Host.parse("foo:bar").isLeft)
  }

  test("parse Access-Control-Allow-Credentials") {
    assert(`Access-Control-Allow-Credentials`.parse("false").isLeft)
    // it is case sensitive
    assert(`Access-Control-Allow-Credentials`.parse("True").isLeft)
  }

  test("SimpleHeaders should parse Last-Modified") {

    val header = `Last-Modified`(HttpDate.Epoch)
    val stringRepr = "Thu, 01 Jan 1970 00:00:00 GMT"
    assertEquals(header.value, stringRepr)
    assertEquals(Header[`Last-Modified`].parse(stringRepr), Right(header))

    assert(Header[`Last-Modified`].parse("foo").isLeft)
  }

  test("SimpleHeaders should parse ETag") {
    assertEquals(ETag.EntityTag("hash", Weak).toString(), "W/\"hash\"")
    assertEquals(ETag.EntityTag("hash", Strong).toString(), "\"hash\"")

    val headers = List("\"hash\"", "W/\"hash\"")

    headers.foreach { header =>
      assertEquals(ETag.parse(header).map(_.value), Right(header))
    }
  }

  test("SimpleHeaders should parse If-None-Match") {
    val headers = List(
      `If-None-Match`(EntityTag("hash")),
      `If-None-Match`(EntityTag("123-999")),
      `If-None-Match`(EntityTag("123-999"), EntityTag("hash")),
      `If-None-Match`(EntityTag("123-999", Weak), EntityTag("hash")),
      `If-None-Match`.`*`,
    )
    headers.foreach { header =>
      assertEquals(`If-None-Match`.parse(header.value), Right(header))
    }
  }

  test("parse Max-Forwards") {
    val headers = List(
      `Max-Forwards`.unsafeFromLong(0),
      `Max-Forwards`.unsafeFromLong(100),
    )
    headers.foreach { header =>
      assertEquals(Header[`Max-Forwards`].parse(header.value), Right(header))
    }
  }

  test("SimpleHeaders should parse Transfer-Encoding") {
    val header = `Transfer-Encoding`(TransferCoding.chunked)
    assertEquals(`Transfer-Encoding`.parse(header.value), Right(header))

    val header2 = `Transfer-Encoding`(TransferCoding.compress)
    assertEquals(`Transfer-Encoding`.parse(header2.value), Right(header2))
  }

  test("SimpleHeaders should parse User-Agent") {
    val header = `User-Agent`(ProductId("foo", Some("bar")), List(ProductId("foo")))
    assertEquals(header.value, "foo/bar foo")

    assertEquals(`User-Agent`.parse(100)(header.value), Right(header))

    val header2 =
      `User-Agent`(ProductId("foo"), List(ProductId("bar", Some("biz")), ProductComment("blah")))
    assertEquals(header2.value, "foo bar/biz (blah)")
    assertEquals(`User-Agent`.parse(188)(header2.value), Right(header2))

    val headerstr = "Mozilla/5.0 (Android; Mobile; rv:30.0) Gecko/30.0 Firefox/30.0"

    val headerraw = Header.Raw(`User-Agent`.name, headerstr)

    val parsed = `User-Agent`.parse(100)(headerraw.value)
    assertEquals(
      parsed,
      Right(
        `User-Agent`(
          ProductId("Mozilla", Some("5.0")),
          List(
            ProductComment("Android; Mobile; rv:30.0"),
            ProductId("Gecko", Some("30.0")),
            ProductId("Firefox", Some("30.0")),
          ),
        )
      ),
    )
    assertEquals(parsed.map(_.value), Right(headerstr))
  }

  test("parse Server") {
    val header = Server(ProductId("foo", Some("bar")), List(ProductComment("foo")))
    assertEquals(header.value, "foo/bar (foo)")

    assertEquals(Server.parse(100)(header.toRaw1.value), Right(header))

    val header2 =
      Server(ProductId("foo"), List(ProductId("bar", Some("biz")), ProductComment("blah")))
    assertEquals(header2.value, "foo bar/biz (blah)")
    assertEquals(Server.parse(100)(header2.toRaw1.value), Right(header2))

    val headerstr = "nginx/1.14.0 (Ubuntu)"
    assertEquals(
      Server.parse(100)(headerstr),
      Right(
        Server(
          ProductId("nginx", Some("1.14.0")),
          List(
            ProductComment("Ubuntu")
          ),
        )
      ),
    )

    val headerstr2 = "CERN/3.0 libwww/2.17"
    assertEquals(
      Server.parse(100)(headerstr2),
      Right(
        Server(
          ProductId("CERN", Some("3.0")),
          List(
            ProductId("libwww", Some("2.17"))
          ),
        )
      ),
    )
  }

  test("SimpleHeaders should parse X-Forwarded-For") {
    // ipv4
    val header2 = `X-Forwarded-For`(NonEmptyList.of(Some(ipv4"127.0.0.1")))
    assertEquals(`X-Forwarded-For`.parse(header2.toRaw1.value), Right(header2))

    // ipv6
    val header3 = `X-Forwarded-For`(
      NonEmptyList.of(Some(ipv6"::1"), Some(ipv6"2001:0db8:85a3:0000:0000:8a2e:0370:7334"))
    )
    assertEquals(`X-Forwarded-For`.parse(header3.toRaw1.value), Right(header3))

    // "unknown"
    val header4 = `X-Forwarded-For`(NonEmptyList.of(None))
    assertEquals(`X-Forwarded-For`.parse(header4.toRaw1.value), Right(header4))

    val bad = "foo"
    assert(`X-Forwarded-For`.parse(bad).isLeft)

    val bad2 = "256.56.56.56"
    assert(`X-Forwarded-For`.parse(bad2).isLeft)
  }

  test("SimpleHeaders should parse X-Forwarded-Host") {
    val header1 = headers.`X-Forwarded-Host`("foo", Some(5))
    assertEquals(headers.`X-Forwarded-Host`.parse("foo:5"), Right(header1))

    val header2 = headers.`X-Forwarded-Host`("foo", None)
    assertEquals(headers.`X-Forwarded-Host`.parse("foo"), Right(header2))

    assert(headers.`X-Forwarded-Host`.parse("foo:bar").isLeft)
  }

}
