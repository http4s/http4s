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

import cats.effect.IO
import org.http4s.Charset._
import org.http4s.implicits._
import org.http4s.headers._
import org.specs2.mutable.Specification

class ResponderSpec extends Specification {
  val resp = Response[IO](Status.Ok)

  "Responder" should {
    "Change status" in {
      val resp = Response[IO](Status.Ok)

      resp.status must_== (Status.Ok)

      resp.withStatus(Status.BadGateway).status must_== (Status.BadGateway)
    }

    "Replace content type" in {
      resp.contentType should be(None)
      val c1 = resp
        .putHeaders(`Content-Length`.unsafeFromLong(4))
        .withContentType(`Content-Type`(MediaType.text.plain))
        .putHeaders(Host("foo"))

      c1.headers.count(_.is(`Content-Type`)) must_== 1
      c1.headers.count(_.is(`Content-Length`)) must_== 1
      (c1.headers.toList must have).length(3)
      c1.contentType must beSome(`Content-Type`(MediaType.text.plain))

      val c2 = c1.withContentType(`Content-Type`(MediaType.application.json, `UTF-8`))

      c2.contentType must beSome(`Content-Type`(MediaType.application.json, `UTF-8`))
      c2.headers.count(_.is(`Content-Type`)) must_== 1
      c2.headers.count(_.is(`Content-Length`)) must_== 1
      c2.headers.count(_.is(Host)) must_== 1
    }

    "Remove headers" in {
      val wHeader = resp.putHeaders(Connection("close".ci))
      wHeader.headers.get(Connection) must beSome(Connection("close".ci))

      val newHeaders = wHeader.removeHeader(Connection)
      newHeaders.headers.get(Connection) must beNone
    }

    "Replace all headers" in {
      val wHeader =
        resp.putHeaders(Connection("close".ci), `Content-Length`.unsafeFromLong(10), Host("foo"))
      (wHeader.headers.toList must have).length(3)

      val newHeaders = wHeader.withHeaders(Date(HttpDate.Epoch))
      (newHeaders.headers.toList must have).length(1)
      newHeaders.headers.get(Connection) must beNone
    }

    "Replace all headers II" in {
      val wHeader =
        resp.putHeaders(Connection("close".ci), `Content-Length`.unsafeFromLong(10), Host("foo"))
      (wHeader.headers.toList must have).length(3)

      val newHeaders = wHeader.withHeaders(Headers.of(Date(HttpDate.Epoch)))
      (newHeaders.headers.toList must have).length(1)
      newHeaders.headers.get(Connection) must beNone
    }

    "Filter headers" in {
      val wHeader =
        resp.putHeaders(Connection("close".ci), `Content-Length`.unsafeFromLong(10), Host("foo"))
      (wHeader.headers.toList must have).length(3)

      val newHeaders = wHeader.filterHeaders(_.name != "Connection".ci)
      (newHeaders.headers.toList must have).length(2)
      newHeaders.headers.get(Connection) must beNone
    }

    "Set cookie from tuple" in {
      resp.addCookie("foo", "bar").cookies must_== List(ResponseCookie("foo", "bar"))
    }

    "Set cookie from Cookie" in {
      resp.addCookie(ResponseCookie("foo", "bar")).cookies must_== List(
        ResponseCookie("foo", "bar"))
    }

    "Set multiple cookies" in {
      resp
        .addCookie(ResponseCookie("foo", "bar"))
        .addCookie(ResponseCookie("baz", "quux"))
        .cookies must_== List(
        ResponseCookie("foo", "bar"),
        ResponseCookie("baz", "quux")
      )
    }

    "Remove cookie" in {
      val cookie = ResponseCookie("foo", "bar")
      resp.removeCookie(cookie).cookies must_== List(
        ResponseCookie("foo", "", expires = Option(HttpDate.Epoch), maxAge = Some(0L))
      )
    }
  }
}
