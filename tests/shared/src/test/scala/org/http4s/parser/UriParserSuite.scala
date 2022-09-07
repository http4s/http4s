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

package org.http4s.parser

import com.comcast.ip4s._
import org.http4s.Uri.Scheme.https
import org.http4s.Uri._
import org.http4s._
import org.http4s.syntax.all._
import org.typelevel.ci._

import scala.collection.immutable

class UriParserSuite extends Http4sSuite {
  private def checkRequestTarget(items: Seq[(String, Uri)]) =
    items.foreach { case (str, uri) =>
      assertEquals(Uri.requestTarget(str), Right(uri))
    }

  // RFC 3986 examples
  // https://datatracker.ietf.org/doc/html/rfc3986#section-1.1.2

  // http://www.ietf.org/rfc/rfc2396.txt

  test("Uri.requestTarget should parse a IPv6 address") {
    val v = "1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab" +: (for {
      h <- 0 to 7
      l <- 0 to 7 - h
      f = List.fill(h)("1ab").mkString(":")
      b = List.fill(l)("32ba").mkString(":")
      if (f ++ b).size < 7 // a single shortened section is disallowed
    } yield f + "::" + b)

    v.foreach { s =>
      assertEquals(Uri.Ipv6Address.fromString(s).map(_.value), Right(s))
    }
  }

  test("Uri.requestTarget should parse a IPv4 address") {
    (0 to 255).foreach { i =>
      val addr = s"$i.$i.$i.$i"
      assertEquals(Uri.Ipv4Address.fromString(addr).map(_.value), Right(addr))
    }
  }

  test("Uri.requestTarget should parse a short IPv6 address in brackets") {
    val s = "[01ab::32ba:32ba]"
    assertEquals(
      Uri.requestTarget(s),
      Right(Uri(authority = Some(Authority(host = Uri.Ipv6Address(ipv6"01ab::32ba:32ba"))))),
    )
  }

  test("Uri.requestTarget should handle port configurations") {
    val portExamples = List(
      (
        "http://foo.com",
        Uri(Some(Scheme.http), Some(Authority(host = RegName(ci"foo.com"), port = None))),
      ),
      (
        "http://foo.com:",
        Uri(Some(Scheme.http), Some(Authority(host = RegName(ci"foo.com"), port = None))),
      ),
      (
        "http://foo.com:80",
        Uri(Some(Scheme.http), Some(Authority(host = RegName(ci"foo.com"), port = Some(80)))),
      ),
    )

    checkRequestTarget(portExamples)
  }

  test("Uri.requestTarget should parse absolute URIs") {
    val absoluteUris = List(
      (
        "http://www.foo.com",
        Uri(Some(Scheme.http), Some(Authority(host = RegName(ci"www.foo.com")))),
      ),
      (
        "http://www.foo.com/foo?bar=baz",
        Uri(
          Some(Scheme.http),
          Some(Authority(host = RegName(ci"www.foo.com"))),
          path"/foo",
          Query.fromPairs("bar" -> "baz"),
        ),
      ),
      (
        "http://192.168.1.1",
        Uri(Some(Scheme.http), Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1")))),
      ),
      (
        "http://192.168.1.1:80/c?GB=object&Class=one",
        Uri(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1"), port = Some(80))),
          path"/c",
          Query.fromPairs("GB" -> "object", "Class" -> "one"),
        ),
      ),
      (
        "http://[2001:db8::7]/c?GB=object&Class=one",
        Uri(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv6Address(ipv6"2001:db8::7"))),
          path"/c",
          Query.fromPairs("GB" -> "object", "Class" -> "one"),
        ),
      ),
      (
        "mailto:John.Doe@example.com",
        Uri(Some(scheme"mailto"), path = path"John.Doe@example.com"),
      ),
    )

    checkRequestTarget(absoluteUris)
  }

  test("Uri.requestTarget should parse relative URIs") {
    val relativeUris = List(
      ("/foo/bar", Uri(path = path"/foo/bar")),
      (
        "/foo/bar?foo=bar&ding=dong",
        Uri(path = path"/foo/bar", query = Query.fromPairs("foo" -> "bar", "ding" -> "dong")),
      ),
      ("/", Uri(path = Uri.Path.Root)),
    )

    checkRequestTarget(relativeUris)
  }

  test("Uri.requestTarget should parse relative URI with empty query string") {
    val u = Uri.requestTarget("/foo/bar?")
    assertEquals(u, Right(Uri(path = path"/foo/bar", query = Query("" -> None))))
  }

  private val q = Query.unsafeFromString("param1=3&param2=2&param2=foo")
  private val u = Uri(query = q)
  test("Uri.requestTarget should represent query as multiParams as a Map[String,Seq[String]]") {
    assertEquals(
      u.multiParams,
      Map("param1" -> immutable.Seq("3"), "param2" -> immutable.Seq("2", "foo")),
    )
  }

  test(
    "Uri.requestTarget should parse query and represent params as a Map[String,String] taking the first param"
  ) {
    assertEquals(u.params, Map("param1" -> "3", "param2" -> "2"))
  }

  test("Uri.requestTarget should fail on invalid uri") {
    val invalid = List("^", "]", "/hello/wo%2rld", "/hello/world?bad=enc%ode")
    assert(invalid.forall { i =>
      Uri.fromString(i).isLeft && Uri.requestTarget(i).isLeft
    })
  }

  private def checkFromString(items: Seq[(String, Uri)]): Unit =
    items.foreach { case (str, uri) =>
      assertEquals(Uri.fromString(str), Right(uri))
    }

  test("Uri.fromString should parse absolute URIs") {
    val absoluteUris = List(
      (
        "http://www.foo.com",
        Uri(Some(Scheme.http), Some(Authority(host = RegName(ci"www.foo.com")))),
      ),
      (
        "http://www.foo.com/foo?bar=baz",
        Uri(
          Some(Scheme.http),
          Some(Authority(host = RegName(ci"www.foo.com"))),
          path"/foo",
          Query.fromPairs("bar" -> "baz"),
        ),
      ),
      (
        "http://192.168.1.1",
        Uri(Some(Scheme.http), Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1")))),
      ),
      (
        "http://192.168.1.1:80/c?GB=object&Class=one",
        Uri(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1"), port = Some(80))),
          path"/c",
          Query.fromPairs("GB" -> "object", "Class" -> "one"),
        ),
      ),
      (
        "http://[2001:db8::7]/c?GB=object&Class=one",
        Uri(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv6Address(ipv6"2001:db8::7"))),
          path"/c",
          Query.fromPairs("GB" -> "object", "Class" -> "one"),
        ),
      ),
      (
        "mailto:John.Doe@example.com",
        Uri(Some(scheme"mailto"), path = path"John.Doe@example.com"),
      ),
    )

    checkFromString(absoluteUris)
  }

  test("Uri.fromString should parse a path-noscheme uri") {
    assertEquals(
      Uri.fromString("q"),
      Right(
        Uri(path = path"q")
      ),
    )
    assertEquals(
      Uri.fromString("a/b"),
      Right(
        Uri(path = path"a/b")
      ),
    )
  }

  test("Uri.fromString should parse a path-noscheme uri with query") {
    assertEquals(
      Uri.fromString("a/b?foo"),
      Right(
        Uri(path = path"a/b", query = Query(("foo", None)))
      ),
    )
  }

  test("Uri.fromString should parse a path-absolute uri") {
    assertEquals(
      Uri.fromString("/a/b"),
      Right(
        Uri(path = path"/a/b")
      ),
    )
  }
  test("Uri.fromString should parse a path-absolute uri with query") {
    assertEquals(
      Uri.fromString("/a/b?foo"),
      Right(
        Uri(path = path"/a/b", query = Query(("foo", None)))
      ),
    )
  }
  test("Uri.fromString should parse a path-absolute uri with query and fragment") {
    assertEquals(
      Uri.fromString("/a/b?foo#bar"),
      Right(
        Uri(path = path"/a/b", query = Query(("foo", None)), fragment = Some("bar"))
      ),
    )
  }

  test("String interpolator should parse valid URIs") {
    assertEquals(
      uri"https://http4s.org",
      Uri(
        scheme = Option(https),
        authority = Option(Uri.Authority(host = RegName(ci"http4s.org"))),
      ),
    )
  }

  test("String interpolator should reject invalid URIs") {
    assert(compileErrors {
      """uri"not valid""""
    }.nonEmpty)
  }
}
