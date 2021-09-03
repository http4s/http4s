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

import cats.syntax.all._
import java.nio.charset.{Charset => NioCharset, StandardCharsets}

import org.http4s.Uri.Scheme.https
import org.http4s._
import org.http4s.Uri._
import org.http4s.internal.parboiled2._
import org.http4s.syntax.all._

class IpParserImpl(val input: ParserInput, val charset: NioCharset) extends Parser with IpParser {
  def CaptureIPv6: Rule1[String] = rule(capture(IpV6Address))
  def CaptureIPv4: Rule1[String] = rule(capture(IpV4Address))
}

class UriParserSuite extends Http4sSuite {
  {
    def check(items: Seq[(String, Uri)]) =
      items.foreach { case (str, uri) =>
        assertEquals(Uri.requestTarget(str), Right(uri))
      }

    // RFC 3986 examples
    // http://tools.ietf.org/html/rfc3986#section-1.1.2

    // http://www.ietf.org/rfc/rfc2396.txt

    test("Uri.requestTarget should parse a IPv6 address") {
      val v = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
        if (f ++ b).size < 7 // a single shortened section is disallowed
      } yield f + "::" + b)

      v.foreach { s =>
        assertEquals(
          Either.fromTry(new IpParserImpl(s, StandardCharsets.UTF_8).CaptureIPv6.run()),
          Right(s))
      }
    }

    test("Uri.requestTarget should parse a IPv4 address") {
      (0 to 255).foreach { i =>
        val addr = s"$i.$i.$i.$i"
        assertEquals(
          Either.fromTry(new IpParserImpl(addr, StandardCharsets.UTF_8).CaptureIPv4.run()),
          Right(addr))
      }
    }

    test("Uri.requestTarget should parse a short IPv6 address in brackets") {
      val s = "[01ab::32ba:32ba]"
      assertEquals(
        Uri.requestTarget(s),
        Right(Uri(authority = Some(Authority(host = ipv6"01ab::32ba:32ba")))))
    }

    test("Uri.requestTarget should handle port configurations") {
      val portExamples: Seq[(String, Uri)] = Seq(
        (
          "http://foo.com",
          Uri(Some(Scheme.http), Some(Authority(host = RegName("foo.com".ci), port = None)))),
        (
          "http://foo.com:",
          Uri(Some(Scheme.http), Some(Authority(host = RegName("foo.com".ci), port = None)))),
        (
          "http://foo.com:80",
          Uri(Some(Scheme.http), Some(Authority(host = RegName("foo.com".ci), port = Some(80)))))
      )

      check(portExamples)
    }

    test("Uri.requestTarget should parse absolute URIs") {
      val absoluteUris: Seq[(String, Uri)] = Seq(
        (
          "http://www.foo.com",
          Uri(Some(Scheme.http), Some(Authority(host = RegName("www.foo.com".ci))))),
        (
          "http://www.foo.com/foo?bar=baz",
          Uri(
            Some(Scheme.http),
            Some(Authority(host = RegName("www.foo.com".ci))),
            "/foo",
            Query.fromPairs("bar" -> "baz"))),
        ("http://192.168.1.1", Uri(Some(Scheme.http), Some(Authority(host = ipv4"192.168.1.1")))),
        (
          "http://192.168.1.1:80/c?GB=object&Class=one",
          Uri(
            Some(Scheme.http),
            Some(Authority(host = ipv4"192.168.1.1", port = Some(80))),
            "/c",
            Query.fromPairs("GB" -> "object", "Class" -> "one"))),
        (
          "http://[2001:db8::7]/c?GB=object&Class=one",
          Uri(
            Some(Scheme.http),
            Some(Authority(host = ipv6"2001:db8::7")),
            "/c",
            Query.fromPairs("GB" -> "object", "Class" -> "one"))),
        ("mailto:John.Doe@example.com", Uri(Some(scheme"mailto"), path = "John.Doe@example.com"))
      )

      check(absoluteUris)
    }

    test("Uri.requestTarget should parse relative URIs") {
      val relativeUris: Seq[(String, Uri)] = Seq(
        ("/foo/bar", Uri(path = "/foo/bar")),
        (
          "/foo/bar?foo=bar&ding=dong",
          Uri(path = "/foo/bar", query = Query.fromPairs("foo" -> "bar", "ding" -> "dong"))),
        ("/", Uri(path = "/"))
      )

      check(relativeUris)
    }

    test("Uri.requestTarget should parse absolute URI with fragment") {
      val u = Uri.requestTarget("http://foo.bar/foo#Examples")
      assertEquals(
        u,
        Right(
          Uri(
            Some(Scheme.http),
            Some(Authority(host = RegName("foo.bar".ci))),
            "/foo",
            Query.empty,
            Some("Examples"))))
    }

    test("Uri.requestTarget should parse absolute URI with parameters and fragment") {
      val u = Uri.requestTarget("http://foo.bar/foo?bar=baz#Example-Fragment")
      assertEquals(
        u,
        Right(
          Uri(
            Some(Scheme.http),
            Some(Authority(host = RegName("foo.bar".ci))),
            "/foo",
            Query.fromPairs("bar" -> "baz"),
            Some("Example-Fragment"))))
    }

    test("Uri.requestTarget should parse relative URI with empty query string") {
      val u = Uri.requestTarget("/foo/bar?")
      assertEquals(u, Right(Uri(path = "/foo/bar", query = Query("" -> None))))
    }

    test(
      "Uri.requestTarget should parse relative URI with empty query string followed by empty fragment") {
      val u = Uri.requestTarget("/foo/bar?#")
      assertEquals(u, Right(Uri(path = "/foo/bar", query = Query("" -> None), fragment = Some(""))))
    }

    test(
      "Uri.requestTarget should parse relative URI with empty query string followed by fragment") {
      val u = Uri.requestTarget("/foo/bar?#Example_of_Fragment")
      assertEquals(
        u,
        Right(
          Uri(
            path = "/foo/bar",
            query = Query("" -> None),
            fragment = Some("Example_of_Fragment"))))
    }

    test("Uri.requestTarget should parse relative URI with fragment") {
      val u = Uri.requestTarget("/foo/bar#Examples_of_Fragment")
      assertEquals(u, Right(Uri(path = "/foo/bar", fragment = Some("Examples_of_Fragment"))))
    }

    test(
      "Uri.requestTarget should parse relative URI with single parameter without a value followed by a fragment") {
      val u = Uri.requestTarget("/foo/bar?bar#Example_of_Fragment")
      assertEquals(
        u,
        Right(
          Uri(
            path = "/foo/bar",
            query = Query("bar" -> None),
            fragment = Some("Example_of_Fragment"))))
    }

    test("Uri.requestTarget should parse relative URI with parameters and fragment") {
      val u = Uri.requestTarget("/foo/bar?bar=baz#Example_of_Fragment")
      assertEquals(
        u,
        Right(
          Uri(
            path = "/foo/bar",
            query = Query.fromPairs("bar" -> "baz"),
            fragment = Some("Example_of_Fragment"))))
    }

    test("Uri.requestTarget should parse relative URI with slash and fragment") {
      val u = Uri.requestTarget("/#Example_Fragment")
      assertEquals(u, Right(Uri(path = "/", fragment = Some("Example_Fragment"))))
    }

    {
      val q = Query.fromString("param1=3&param2=2&param2=foo")
      val u = Uri(query = q)
      test("Uri.requestTarget should represent query as multiParams as a Map[String,Seq[String]]") {
        assert(u.multiParams == Map("param1" -> Seq("3"), "param2" -> Seq("2", "foo")))
      }

      test(
        "Uri.requestTarget should parse query and represent params as a Map[String,String] taking the first param") {
        assert(u.params == Map("param1" -> "3", "param2" -> "2"))
      }
    }

    test("Uri.requestTarget should fail on invalid uri") {
      val invalid = Seq("^", "]", "/hello/wo%2rld", "/hello/world?bad=enc%ode")
      assert(invalid.forall { i =>
        Uri.fromString(i).isLeft && Uri.requestTarget(i).isLeft
      })
    }
  }

  {
    def check(items: Seq[(String, Uri)]) =
      items.foreach { case (str, uri) =>
        assertEquals(Uri.fromString(str), Right(uri))
      }

    test("Uri.fromString should parse absolute URIs") {
      val absoluteUris: Seq[(String, Uri)] = Seq(
        (
          "http://www.foo.com",
          Uri(Some(Scheme.http), Some(Authority(host = RegName("www.foo.com".ci))))),
        (
          "http://www.foo.com/foo?bar=baz",
          Uri(
            Some(Scheme.http),
            Some(Authority(host = RegName("www.foo.com".ci))),
            "/foo",
            Query.fromPairs("bar" -> "baz"))),
        ("http://192.168.1.1", Uri(Some(Scheme.http), Some(Authority(host = ipv4"192.168.1.1")))),
        (
          "http://192.168.1.1:80/c?GB=object&Class=one",
          Uri(
            Some(Scheme.http),
            Some(Authority(host = ipv4"192.168.1.1", port = Some(80))),
            "/c",
            Query.fromPairs("GB" -> "object", "Class" -> "one"))),
        (
          "http://[2001:db8::7]/c?GB=object&Class=one",
          Uri(
            Some(Scheme.http),
            Some(Authority(host = ipv6"2001:db8::7")),
            "/c",
            Query.fromPairs("GB" -> "object", "Class" -> "one"))),
        ("mailto:John.Doe@example.com", Uri(Some(scheme"mailto"), path = "John.Doe@example.com"))
      )

      check(absoluteUris)
    }

    test("Uri.fromString should parse a path-noscheme uri") {
      assertEquals(
        Uri.fromString("q"),
        Right(
          Uri(path = "q")
        ))
      assertEquals(
        Uri.fromString("a/b"),
        Right(
          Uri(path = "a/b")
        ))
    }

    test("Uri.fromString should parse a path-noscheme uri with query") {
      assertEquals(
        Uri.fromString("a/b?foo"),
        Right(
          Uri(path = "a/b", query = Query(("foo", None)))
        ))
    }

    test("Uri.fromString should parse a path-absolute uri") {
      assertEquals(
        Uri.fromString("/a/b"),
        Right(
          Uri(path = "/a/b")
        ))
    }
    test("Uri.fromString should parse a path-absolute uri with query") {
      assertEquals(
        Uri.fromString("/a/b?foo"),
        Right(
          Uri(path = "/a/b", query = Query(("foo", None)))
        ))
    }
    test("Uri.fromString should parse a path-absolute uri with query and fragment") {
      assertEquals(
        Uri.fromString("/a/b?foo#bar"),
        Right(
          Uri(path = "/a/b", query = Query(("foo", None)), fragment = Some("bar"))
        ))
    }
  }

  {
    test("String interpolator should parse valid URIs") {
      assertEquals(
        uri"https://http4s.org",
        Uri(
          scheme = Option(https),
          authority = Option(Uri.Authority(host = RegName("http4s.org".ci)))))
    }

    test("String interpolator should reject invalid URIs") {
      assertNoDiff(
        compileErrors {
          """uri"not valid""""
        },
        """error: invalid Uri
          |uri"not valid"
          |^
        """.stripMargin)
    }
  }
}
