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
import java.net.InetAddress
import org.http4s.headers._
import org.http4s.syntax.all._
import org.http4s.headers.ETag.EntityTag

class SimpleHeadersSpec extends Http4sSuite {

  test("SimpleHeaders should parse Connection") {
    val header = Connection("closed".ci)
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
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "foo; bar")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("SimpleHeaders should parse Date") { // mills are lost, get rid of them
    val header = Date(HttpDate.Epoch).toRaw.parsed
    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val bad = Header(header.name.toString, "foo")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
  }

  test("SimpleHeaders should parse Host") {
    val header1 = Host("foo", Some(5))
    assertEquals(HttpHeaderParser.parseHeader(header1.toRaw), Right(header1))

    val header2 = Host("foo", None)
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    val bad = Header(header1.name.toString, "foo:bar")
    assert(HttpHeaderParser.parseHeader(bad).isLeft)
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
    assertEquals(ETag.EntityTag("hash", weak = true).toString(), "W/\"hash\"")
    assertEquals(ETag.EntityTag("hash", weak = false).toString(), "\"hash\"")

    val headers = Seq(ETag("hash"), ETag("hash", true))

    headers.foreach { header =>
      assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))
    }
  }

  test("SimpleHeaders should parse If-None-Match") {
    val headers = Seq(
      `If-None-Match`(EntityTag("hash")),
      `If-None-Match`(EntityTag("123-999")),
      `If-None-Match`(EntityTag("123-999"), EntityTag("hash")),
      `If-None-Match`(EntityTag("123-999", weak = true), EntityTag("hash")),
      `If-None-Match`.`*`
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
    val header = `User-Agent`(AgentProduct("foo", Some("bar")), List(AgentComment("foo")))
    assertEquals(header.value, "foo/bar (foo)")

    assertEquals(HttpHeaderParser.parseHeader(header.toRaw), Right(header))

    val header2 = `User-Agent`(
      AgentProduct("foo"),
      List(AgentProduct("bar", Some("biz")), AgentComment("blah")))
    assertEquals(header2.value, "foo bar/biz (blah)")
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    val headerstr = "Mozilla/5.0 (Android; Mobile; rv:30.0) Gecko/30.0 Firefox/30.0"
    assertEquals(
      HttpHeaderParser.parseHeader(Header.Raw(`User-Agent`.name, headerstr)),
      Right(
        `User-Agent`(
          AgentProduct("Mozilla", Some("5.0")),
          List(
            AgentComment("Android; Mobile; rv:30.0"),
            AgentProduct("Gecko", Some("30.0")),
            AgentProduct("Firefox", Some("30.0"))
          )
        )
      )
    )
  }

  test("SimpleHeaders should parse X-Forwarded-For") {
    // ipv4
    val header2 = `X-Forwarded-For`(
      NonEmptyList.of(Some(InetAddress.getLocalHost), Some(InetAddress.getLoopbackAddress)))
    assertEquals(HttpHeaderParser.parseHeader(header2.toRaw), Right(header2))

    // ipv6
    val header3 = `X-Forwarded-For`(
      NonEmptyList.of(
        Some(InetAddress.getByName("::1")),
        Some(InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7334"))))
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
