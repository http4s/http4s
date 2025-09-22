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

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/scalatra/rl/blob/v0.4.10/core/src/test/scala/rl/tests/UrlCodingSpec.scala
 * Copyright (c) 2011 Mojolly Ltd.
 * See licenses/LICENSE_rl
 */

package org.http4s

import cats.kernel.laws.discipline.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.Uri.*
import org.http4s.internal.CharPredicate
import org.http4s.laws.discipline.arbitrary.*
import org.http4s.syntax.all.*
import org.scalacheck.Gen
import org.scalacheck.Prop.*
import org.typelevel.ci.*

// TODO: this needs some more filling out
class UriSuite extends Http4sSuite {
  sealed case class Ttl(seconds: Int)
  object Ttl {
    implicit val queryParamInstance: QueryParamEncoder[Ttl] with QueryParam[Ttl] =
      new QueryParamEncoder[Ttl] with QueryParam[Ttl] {
        def key: QueryParameterKey = QueryParameterKey("ttl")
        def encode(value: Ttl): QueryParameterValue = QueryParameterValue(value.seconds.toString)
      }
  }

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  test("Adding an empty path segment shouldn't override 'endsWithSlash'") {
    assert(
      uri"/test//".addSegment(Path.Segment.empty).path.endsWithSlash
    )
    assert(
      uri"/test//".path.addSegments(Seq.empty).endsWithSlash
    )
    assert(
      uri"/test//".path.addSegments(Seq(Path.Segment.empty)).endsWithSlash
    )
  }

  test("Uri fromString should Not UrlDecode the query String") {
    assertEquals(
      getUri("http://localhost:8080/blah?x=abc&y=ijk").query,
      Query.fromPairs("x" -> "abc", "y" -> "ijk"),
    )
  }

  test("Uri fromString should Not UrlDecode the uri fragment") {
    assertEquals(getUri("http://localhost:8080/blah#x=abc&y=ijk").fragment, Some("x=abc&y=ijk"))
  }

  test("Uri fromString should parse scheme correctly") {
    val uri = getUri("http://localhost/")
    assertEquals(uri.scheme, Some(Scheme.http))
  }

  test("Uri fromString should parse the authority correctly when uri has trailing slash") {
    val uri = getUri("http://localhost/")
    assertEquals(uri.authority.get.host, RegName("localhost"))
  }

  test(
    "Uri fromString should parse the authority correctly when uri does not have trailing slash"
  ) {
    val uri = getUri("http://localhost")
    assertEquals(uri.authority.get.host, RegName("localhost"))
  }

  test("Uri fromString should parse the authority correctly if there is none") {
    val uri = getUri("/foo/bar")
    assertEquals(uri.authority, None)
  }

  test("Uri fromString should parse port correctly if there is a valid (non-negative) one") {
    forAll(Gen.choose[Int](0, Int.MaxValue)) { (nonNegative: Int) =>
      val uri = getUri(s"http://localhost:$nonNegative/")

      assertEquals(uri.port, Some(nonNegative))
    }
  }

  test("Uri fromString should parse port correctly if there is none") {
    val uri = getUri("http://localhost/")
    assertEquals(uri.port, None)
  }

  // See RFC 3986, section 6.2.3
  test("Uri fromString should parse port correctly for an empty String") {
    val uri: Uri = getUri("http://foobar:/")
    assertEquals(uri.port, None)
    assertEquals(uri.scheme, Some(Scheme.http))
    assertEquals(uri.authority.get.host, RegName("foobar"))
  }

  test("Uri fromString should both authority and port") {
    val auth = getUri("http://localhost:8080/").authority.get
    assertEquals(auth.host, RegName("localhost"))
    assertEquals(auth.port, Some(8080))
  }

  test(
    "Uri fromString should provide a useful error message if string argument is not url-encoded"
  ) {
    assertEquals(
      Uri.fromString("http://example.org/a file"),
      Left(
        ParseFailure(
          "Invalid URI",
          """http://example.org/a file
            |                    ^
            |expectation:
            |* must end the string""".stripMargin,
        )
      ),
    )
  }

  test("Uri should fail to parse portif it's negative") {
    forAll(Gen.choose[Int](Int.MinValue, -1)) { (negative: Int) =>
      val uri: ParseResult[Uri] = Uri.fromString(s"http://localhost:$negative/")
      uri match {
        case Left(ParseFailure("Invalid URI", _)) => true
        case _ => false
      }
    }
  }

  test("Uri should fail to parse portif it's larger than Int.MaxValue") {
    forAll(Gen.choose[Long]((Int.MaxValue: Long) + 1, Long.MaxValue)) { (tooBig: Long) =>
      val uri: ParseResult[Uri] = Uri.fromString(s"http://localhost:$tooBig/")
      uri match {
        case Left(ParseFailure("Invalid URI", _)) => true
        case _ => false
      }
    }
  }

  test("Uri should fail to parse portif it's not a number or an empty String") {
    forAll(
      Gen.alphaNumStr.suchThat(str =>
        str.nonEmpty && Either.catchOnly[NumberFormatException](str.toInt).isLeft
      )
    ) { (notNumber: String) =>
      val uri: ParseResult[Uri] = Uri.fromString(s"http://localhost:$notNumber/")
      uri match {
        case Left(ParseFailure("Invalid URI", _)) => true
        case _ => false
      }
    }
  }

  test("Uri should support a '/' operator when original uri has trailing slash") {
    val uri = getUri("http://localhost:8080/")
    val newUri = uri / "echo"
    assertEquals(newUri, getUri("http://localhost:8080/echo"))
  }

  test("Uri should support a '/' operator when original uri has no trailing slash") {
    val uri = getUri("http://localhost:8080")
    val newUri = uri / "echo"
    assertEquals(newUri, getUri("http://localhost:8080/echo"))
  }

  test("Uri's with a query and fragment should parse properly") {
    val uri = getUri("http://localhost:8080/blah?x=abc#y=ijk")
    assertEquals(uri.query, Query.fromPairs("x" -> "abc"))
    assertEquals(uri.fragment, Some("y=ijk"))
  }

  def getQueryParams(uri: String): Map[String, String] = getUri(uri).params

  test("Uri Query decoding should Handle queries with no spaces properly") {
    assertEquals(
      getQueryParams("http://localhost:8080/blah?x=abc&y=ijk"),
      Map("x" -> "abc", "y" -> "ijk"),
    )
    assertEquals(getQueryParams("http://localhost:8080/blah?"), Map("" -> ""))
    assertEquals(getQueryParams("http://localhost:8080/blah"), Map.empty[String, String])
  }

  test("Uri Query decoding should Handle queries with spaces properly") {
    // Issue #75
    assertEquals(
      getQueryParams("http://localhost:8080/blah?x=a+bc&y=ijk"),
      Map("x" -> "a bc", "y" -> "ijk"),
    )
    assertEquals(
      getQueryParams("http://localhost:8080/blah?x=a%20bc&y=ijk"),
      Map("x" -> "a bc", "y" -> "ijk"),
    )
  }

  test("Uri copy should support updating the schema") {
    assertEquals(
      uri"http://example.com/".copy(scheme = Scheme.https.some),
      uri"https://example.com/",
    )
    // Must add the authority to set the scheme and host
    assertEquals(
      uri"/route/".copy(
        scheme = Scheme.https.some,
        authority = Some(Authority(None, RegName("example.com"))),
      ),
      uri"https://example.com/route/",
    )
    // You can add a port too
    assertEquals(
      uri"/route/".copy(
        scheme = Scheme.https.some,
        authority = Some(Authority(None, RegName("example.com"), Some(8443))),
      ),
      uri"https://example.com:8443/route/",
    )
  }

  test("Uri toString should render default URI") {
    assertEquals(Uri().toString, "")
  }

  test("Uri toString should withPath without slash adds a / on render") {
    val uri = getUri("http://localhost/foo/bar/baz")
    assertEquals(uri.withPath(path"bar").toString, "http://localhost/bar")
  }

  test("Uri toString should render a IPv6 address, should be wrapped in brackets") {
    val variants = "1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab" +: (for {
      h <- 0 to 6
      l <- 0 to 6 - h
      f = List.fill(h)("1ab").mkString(":")
      b = List.fill(l)("32ba").mkString(":")
    } yield f + "::" + b)

    variants.foreach { s =>
      assertEquals(
        Uri(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv6Address.unsafeFromString(s))),
          path"/foo",
          Query.fromPairs("bar" -> "baz"),
        ).toString,
        s"http://[$s]/foo?bar=baz",
      )
    }
  }

  test("Uri toString should render URL with parameters") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(Authority(host = RegName(ci"www.foo.com"))),
        path"/foo",
        Query.fromPairs("bar" -> "baz"),
      ).toString,
      "http://www.foo.com/foo?bar=baz",
    )
  }

  test("Uri toString should render URL with port") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(Authority(host = RegName(ci"www.foo.com"), port = Some(80))),
      ).toString,
      "http://www.foo.com:80",
    )
  }

  test("Uri toString should render URL without port") {
    assertEquals(
      Uri(Some(Scheme.http), Some(Authority(host = RegName(ci"www.foo.com")))).toString,
      "http://www.foo.com",
    )
  }

  test("Uri toString should render IPv4 URL with parameters") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1"), port = Some(80))),
        path"/c",
        Query.fromPairs("GB" -> "object", "Class" -> "one"),
      ).toString,
      "http://192.168.1.1:80/c?GB=object&Class=one",
    )
  }

  test("Uri toString should render IPv4 URL with port") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1"), port = Some(8080))),
      ).toString,
      "http://192.168.1.1:8080",
    )
  }

  test("Uri toString should render IPv4 URL without port") {
    assertEquals(
      Uri(Some(Scheme.http), Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1")))).toString,
      "http://192.168.1.1",
    )
  }

  test("Uri toString should render IPv6 URL with parameters") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(Authority(host = Uri.Ipv6Address(ipv6"2001:db8::7"))),
        path"/c",
        Query.fromPairs("GB" -> "object", "Class" -> "one"),
      ).toString,
      "http://[2001:db8::7]/c?GB=object&Class=one",
    )
  }

  test("Uri toString should render IPv6 URL with port") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(
          Authority(
            host = Uri.Ipv6Address(ipv6"2001:db8:85a3:8d3:1319:8a2e:370:7344"),
            port = Some(8080),
          )
        ),
      ).toString,
      "http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]:8080",
    )
  }

  test("Uri toString should render IPv6 URL without port") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(Authority(host = Uri.Ipv6Address(ipv6"2001:db8:85a3:8d3:1319:8a2e:370:7344"))),
      ).toString,
      "http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]",
    )
  }

  test("Uri toString should not append a '/' unless it's in the path") {
    assertEquals(uri"http://www.example.com".toString, "http://www.example.com")
  }

  test("Uri toString should render email address") {
    assertEquals(
      Uri(Some(scheme"mailto"), path = path"John.Doe@example.com").toString,
      "mailto:John.Doe@example.com",
    )
  }

  test("Uri toString should render an URL with username and password") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(
          Authority(
            Some(UserInfo("username", Some("password"))),
            RegName("some.example.com"),
            None,
          )
        ),
        path"/",
        Query.empty,
        None,
      ).toString,
      "http://username:password@some.example.com/",
    )
  }

  test("Uri toString should render an URL with username and password, path and params") {
    assertEquals(
      Uri(
        Some(Scheme.http),
        Some(
          Authority(
            Some(UserInfo("username", Some("password"))),
            RegName("some.example.com"),
            None,
          )
        ),
        path"/some/path",
        Query.unsafeFromString("param1=5&param-without-value"),
        None,
      ).toString,
      "http://username:password@some.example.com/some/path?param1=5&param-without-value",
    )
  }

  test("Uri toString should render relative URI with empty query string") {
    assertEquals(
      Uri(path = path"/", query = Query.unsafeFromString(""), fragment = None).toString,
      "/?",
    )
  }

  test("Uri toString should render relative URI with empty query string and fragment") {
    assertEquals(
      Uri(path = path"/", query = Query.unsafeFromString(""), fragment = Some("")).toString,
      "/?#",
    )
  }

  test("Uri toString should render relative URI with empty fragment") {
    assertEquals(
      Uri(path = Uri.Path.Root, query = Query.empty, fragment = Some("")).toString,
      "/#",
    )
  }

  test("Uri toString should render relative URI with empty fragment") {
    assertEquals(
      Uri(path = Uri.Path.Root, query = Query.empty, fragment = Some("")).toString,
      "/#",
    )
  }

  test("Uri toString should render absolute path with fragment") {
    assertEquals(
      Uri(path = path"/foo/bar", fragment = Some("an-anchor")).toString,
      "/foo/bar#an-anchor",
    )
  }

  test("Uri toString should render absolute path with parameters") {
    assertEquals(
      Uri(path = path"/foo/bar", query = Query.unsafeFromString("foo=bar&ding=dong")).toString,
      "/foo/bar?foo=bar&ding=dong",
    )
  }

  test("Uri toString should render absolute path with parameters and fragment") {
    assertEquals(
      Uri(
        path = path"/foo/bar",
        query = Query.unsafeFromString("foo=bar&ding=dong"),
        fragment = Some("an_anchor"),
      ).toString,
      "/foo/bar?foo=bar&ding=dong#an_anchor",
    )
  }

  test("Uri toString should render absolute path with parameters and fragment") {
    assertEquals(
      Uri(
        path = path"/foo/bar",
        query = Query.unsafeFromString("foo=bar&ding=dong"),
        fragment = Some("an_anchor"),
      ).toString,
      "/foo/bar?foo=bar&ding=dong#an_anchor",
    )
  }

  test("Uri toString should render absolute path without parameters") {
    assertEquals(Uri(path = path"/foo/bar").toString, "/foo/bar")
  }

  test("Uri toString should render absolute root path without parameters") {
    assertEquals(Uri(path = path"/").toString, "/")
  }

  test("Uri toString should render absolute path containing colon") {
    assertEquals(Uri(path = path"/foo:bar").toString, "/foo:bar")
  }

  test(
    "Uri toString should prefix relative path containing colon in the only segment with a ./"
  ) {
    assertEquals(Uri(path = path"foo:bar").toString, "./foo:bar")
  }

  test("Uri toString should prefix relative path containing colon in first segment with a ./") {
    assertEquals(Uri(path = path"foo:bar/baz").toString, "./foo:bar/baz")
  }

  test("Uri toString should not prefix relative path containing colon in later segment") {
    assertEquals(Uri(path = path"foo/bar:baz").toString, "foo/bar:baz")
  }

  test("Uri toString should render a query string with a single param") {
    assertEquals(Uri(query = Query.unsafeFromString("param1=test")).toString, "?param1=test")
  }

  test("Uri toString should render a query string with multiple value in a param") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=3&param2=2&param2=foo")).toString,
      "?param1=3&param2=2&param2=foo",
    )
  }

  test("Uri toString should round trip over URI examples from wikipedia") {
    /*
     * Examples from:
     * - http://de.wikipedia.org/wiki/Uniform_Resource_Identifier
     * - http://en.wikipedia.org/wiki/Uniform_Resource_Identifier
     *
     * URI.fromString fails for:
     * - "//example.org/scheme-relative/URI/with/absolute/path/to/resource.txt",
     */
    val examples = List(
      "http://de.wikipedia.org/wiki/Uniform_Resource_Identifier",
      "ftp://ftp.is.co.za/rfc/rfc1808.txt",
      "geo:48.33,14.122;u=22.5",
      "ldap://[2001:db8::7]/c=GB?objectClass?one",
      "gopher://gopher.floodgap.com",
      "mailto:John.Doe@example.com",
      "sip:911@pbx.mycompany.com",
      "news:comp.infosystems.www.servers.unix",
      "data:text/plain;charset=iso-8859-7,%be%fa%be",
      "tel:+1-816-555-1212",
      "telnet://192.0.2.16:80",
      "urn:oasis:names:specification:docbook:dtd:xml:4.1.2",
      "git://github.com/rails/rails.git",
      "crid://broadcaster.com/movies/BestActionMovieEver",
      "http://example.org/absolute/URI/with/absolute/path/to/resource.txt",
      "/relative/URI/with/absolute/path/to/resource.txt",
      "http://en.wikipedia.org/wiki/URI#Examples_of_URI_references",
      "file:///C:/Users/Benutzer/Desktop/Uniform%20Resource%20Identifier.html",
      "file:///etc/fstab",
      "relative/path/to/resource.txt",
      "../../../resource.txt",
      "./resource.txt#frag01",
      "resource.txt",
      "#frag01",
      "",
    )
    examples.foreach { e =>
      assertEquals(Uri.fromString(e).map(_.toString), Right(e))
    }
  }

  test("Uri toString should handle brackets in query string") {
    assertEquals(
      Uri
        .fromString("http://localhost:8080/index?filter[state]=public")
        .map(_.toString),
      Right("http://localhost:8080/index?filter[state]=public"),
    )
  }

  test("Uri toString should round trip with toString".fail) {
    forAll { (uri: Uri) =>
      assertEquals(Uri.fromString(uri.toString), Right(uri))
    }
    fail("known to fail from time to time")
  }

  test("Uri parameters should parse empty query string") {
    assertEquals(
      Uri(query = Query.unsafeFromString("")).multiParams,
      Map[String, Seq[String]]("" -> Nil),
    )
  }
  test("Uri parameters should parse parameter without key but with empty value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("=")).multiParams,
      Map[String, Seq[String]]("" -> List("")),
    )
  }
  test("Uri parameters should parse parameter without key but with value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("=value")).multiParams,
      Map("" -> List("value")),
    )
  }
  test("Uri parameters should parse single parameter with empty value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=")).multiParams,
      Map("param1" -> List("")),
    )
  }
  test("Uri parameters should parse single parameter with value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value")).multiParams,
      Map("param1" -> List("value")),
    )
  }
  test("Uri parameters should parse single parameter without value") {
    assertEquals(Uri(query = Query.unsafeFromString("param1")).multiParams, Map("param1" -> Nil))
  }
  test("Uri parameters should parse many parameter with value") {
    assertEquals(
      Uri(query =
        Query.unsafeFromString("param1=value&param2=value1&param2=value2&param3=value")
      ).multiParams,
      Map(
        "param1" -> List("value"),
        "param2" -> List("value1", "value2"),
        "param3" -> List("value"),
      ),
    )
  }
  test("Uri parameters should parse many parameter without value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1&param2&param3")).multiParams,
      Map("param1" -> Nil, "param2" -> Nil, "param3" -> Nil),
    )
  }

  test("Uri.params.+ should add parameter to empty query") {
    val i = Uri(query = Query.empty).params + (("param", "value"))
    assertEquals(i, Map("param" -> "value"))
  }
  test("Uri.params.+ should add parameter") {
    val i = Uri(query = Query.unsafeFromString("param1")).params + (("param2", ""))
    assertEquals(i, Map("param1" -> "", "param2" -> ""))
  }
  test("Uri.params.+ should replace an existing parameter") {
    val i =
      Uri(query = Query.unsafeFromString("param=value")).params + (
        (
          "param",
          List("value1", "value2"),
        )
      )
    assertEquals(i, Map("param" -> List("value1", "value2")))
  }
  test("Uri.params.+ should replace an existing parameter with empty value") {
    val i = Uri(query = Query.unsafeFromString("param=value")).params + (("param", List.empty))
    assertEquals(i, Map("param" -> List.empty))
  }

  test("Uri.params.- should not do anything on an URI without a query") {
    val i = Uri(query = Query.empty).params - "param"
    assertEquals(i, Map.empty[String, String])
  }
  test("Uri.params.- should not reduce a map if parameter does not match") {
    val i = Uri(query = Query.unsafeFromString("param1")).params - "param2"
    assertEquals(i, Map("param1" -> ""))
  }
  test("Uri.params.- should reduce a map if matching parameter found") {
    val i = Uri(query = Query.unsafeFromString("param")).params - "param"
    assertEquals(i, Map.empty[String, String])
  }

  test("Uri.params.iterate should work on an URI without a query") {
    Uri(query = Query.empty).params.toList.foreach { i =>
      fail(s"should not have $i") // should not happen
    }
  }
  test("Uri.params.iterate should work on empty list") {
    Uri(query = Query.unsafeFromString("")).params.toList.foreach { case (k, v) =>
      assertEquals(k, "")
      assertEquals(v, "")
    }
  }
  test("Uri.params.iterate should work with empty keys") {
    val u = Uri(query = Query.unsafeFromString("=value1&=value2&=&"))
    val i = u.params.iterator
    assertEquals(i.next(), "" -> "value1")
    intercept[NoSuchElementException](i.next())
  }
  test("Uri.params.iterate should work on non-empty query string") {
    val u = Uri(
      query = Query.unsafeFromString(
        "param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"
      )
    )
    val i = u.params.iterator
    assertEquals(i.next(), "param1" -> "value1")
    assertEquals(i.next(), "param2" -> "value4")
    intercept[NoSuchElementException](i.next())
  }

  test("Uri.multiParams should find first value of parameter with many values") {
    val u = Uri(
      query = Query.unsafeFromString(
        "param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"
      )
    )
    assertEquals(
      u.multiParams,
      Map("param1" -> Seq("value1", "value2", "value3"), "param2" -> Seq("value4", "value5")),
    )
  }
  test("Uri.multiParams should find parameter with empty key and a value") {
    val u = Uri(query = Query.unsafeFromString("param1=&=value-of-empty-key&param2=value"))
    assertEquals(
      u.multiParams,
      Map("" -> Seq("value-of-empty-key"), "param1" -> Seq(""), "param2" -> Seq("value")),
    )
  }
  test("Uri.multiParams should find first value of parameter with empty key") {
    assertEquals(
      Uri(query = Query.unsafeFromString("=value1&=value2")).multiParams,
      Map("" -> Seq("value1", "value2")),
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("&=value1&=value2")).multiParams,
      Map("" -> Seq("value1", "value2")),
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("&&&=value1&&&=value2&=&")).multiParams,
      Map("" -> Seq("value1", "value2", "")),
    )
  }
  test("Uri.multiParams should find parameter with empty key and without value") {
    assertEquals(Uri(query = Query.unsafeFromString("&")).multiParams, Map("" -> Seq[String]()))
    assertEquals(Uri(query = Query.unsafeFromString("&&")).multiParams, Map("" -> Seq[String]()))
    assertEquals(Uri(query = Query.unsafeFromString("&&&")).multiParams, Map("" -> Seq[String]()))
  }
  test("Uri.multiParams should find parameter with an empty value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=")).multiParams,
      Map("param1" -> Seq("")),
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=&param2=")).multiParams,
      Map("param1" -> Seq(""), "param2" -> Seq("")),
    )
  }
  test("Uri.multiParams should find parameter with single value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value1&param2=value2")).multiParams,
      Map("param1" -> Seq("value1"), "param2" -> Seq("value2")),
    )
  }
  test("Uri.multiParams should find parameter without value") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1&param2&param3")).multiParams,
      Map[String, Seq[String]]("param1" -> Seq(), "param2" -> Seq(), "param3" -> Seq()),
    )
  }

  test("Uri.params.get should find first value of parameter with many values") {
    val u = Uri(
      query = Query.unsafeFromString(
        "param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"
      )
    )
    assertEquals(u.params.get("param1"), Some("value1"))
    assertEquals(u.params.get("param2"), Some("value4"))
  }
  test("Uri.params.get should find parameter with empty key and a value") {
    val u = Uri(query = Query.unsafeFromString("param1=&=valueWithEmptyKey&param2=value2"))
    assertEquals(u.params.get(""), Some("valueWithEmptyKey"))
  }
  test("Uri.params.get should find first value of parameter with empty key") {
    assertEquals(
      Uri(query = Query.unsafeFromString("=value1&=value2")).params.get(""),
      Some("value1"),
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("&=value1&=value2")).params.get(""),
      Some("value1"),
    )
    assertEquals(Uri(query = Query.unsafeFromString("&&&=value1")).params.get(""), Some("value1"))
  }
  test("Uri.params.get should find parameter with empty key and without value") {
    assertEquals(Uri(query = Query.unsafeFromString("&")).params.get(""), Some(""))
    assertEquals(Uri(query = Query.unsafeFromString("&&")).params.get(""), Some(""))
    assertEquals(Uri(query = Query.unsafeFromString("&&&")).params.get(""), Some(""))
  }
  test("Uri.params.get should find parameter with an empty value") {
    val u = Uri(query = Query.unsafeFromString("param1=&param2=value2"))
    assertEquals(u.params.get("param1"), Some(""))
  }
  test("Uri.params.get should find parameter with single value") {
    val u = Uri(query = Query.unsafeFromString("param1=value1&param2=value2"))
    assertEquals(u.params.get("param1"), Some("value1"))
    assertEquals(u.params.get("param2"), Some("value2"))
  }
  test("Uri.params.get should find parameter without value") {
    val u = Uri(query = Query.unsafeFromString("param1&param2&param3"))
    assertEquals(u.params.get("param1"), Some(""))
    assertEquals(u.params.get("param2"), Some(""))
    assertEquals(u.params.get("param3"), Some(""))
  }
  test("Uri.params.get should not find an unknown parameter") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1&param2&param3")).params.get("param4"),
      None,
    )
  }
  test("Uri.params.get should not find anything if query string is empty") {
    assertEquals(Uri(query = Query.empty).params.get("param1"), None)
  }

  test("Uri parameter convenience methods should add a parameter if no query is available") {
    val u = Uri(query = Query.empty) +? ("param1" -> "value")
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=value")))
  }
  test("Uri parameter convenience methods should add a parameter") {
    val u =
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2")) +? ("param2" -> "value")
    assertEquals(
      u,
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2=value")),
    )
  }
  test("Uri parameter convenience methods should add a parameter with boolean value") {
    val u =
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2")) +? ("param2" -> true)
    assertEquals(
      u,
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2=true")),
    )
  }
  test("Uri parameter convenience methods should add a parameter without a value") {
    val u = Uri(query = Query.unsafeFromString("param1=value1&param1=value2")) +? "param2"
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2")))
  }
  test("Uri parameter convenience methods should add a parameter with many values") {
    val u = Uri() ++? ("param1" -> List("value1", "value2"))
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=value1&param1=value2")))
  }
  test("Uri parameter convenience methods should add a parameter with many long values") {
    val u = Uri() ++? ("param1" -> List(1L, -1L))
    assertEquals(u, Uri(query = Query.unsafeFromString(s"param1=1&param1=-1")))
  }
  test(
    "Uri parameter convenience methods should add a query parameter with a QueryParamEncoder"
  ) {
    val u = Uri() +? ("test" -> Ttl(2))
    assertEquals(u, Uri(query = Query.unsafeFromString(s"test=2")))
  }
  test(
    "Uri parameter convenience methods should add a query parameter with a QueryParamEncoder and an implicit key"
  ) {
    val u0 = Uri().+*?(Ttl(2))
    assertEquals(u0, Uri(query = Query.unsafeFromString(s"ttl=2")))
    val u1 = Uri().withQueryParamValue(Ttl(2))
    assertEquals(u1, Uri(query = Query.unsafeFromString(s"ttl=2")))
  }
  test("Uri parameter convenience methods should add a QueryParam instance") {
    val u = Uri().withQueryParam[Ttl]
    assertEquals(u, Uri(query = Query.unsafeFromString(s"ttl")))
  }
  test("Uri parameter convenience methods should add an optional query parameter (Just)") {
    val u = Uri() +?? ("param1" -> Some(2))
    assertEquals(u, Uri(query = Query.unsafeFromString(s"param1=2")))
  }
  test("Uri parameter convenience methods should add an optional query parameter (Empty)") {
    val u = Uri() +?? ("param1" -> Option.empty[Int])
    assertEquals(u, Uri(query = Query.empty))
  }
  test("Uri parameter convenience methods should add multiple query parameters at once") {
    val params = Map("param1" -> 1, "param2" -> 2)
    val u = Uri().withQueryParams(params)
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=1&param2=2")))
  }
  test(
    "Uri parameter convenience methods should add multiple values for same query parameter name"
  ) {
    val params = Map("param1" -> List(1), "param2" -> List(2, 3))
    val u = Uri().withMultiValueQueryParams(params)
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=1&param2=2&param2=3")))
  }
  test("Uri parameter convenience methods should replace an existing parameter") {
    val params = Map("param2" -> 3, "param3" -> 4)
    val u = Uri(query = Query.unsafeFromString("param1=1&param2=2")).withQueryParams(params)
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=1&param2=3&param3=4")))
  }
  test("Uri parameter convenience methods should replace an existing multi-valued parameter") {
    val u = Uri(query = Query.unsafeFromString("param1=1&param1=2"))
      .withQueryParams(Map("param1" -> 3))
      .withMultiValueQueryParams(Map("param2" -> List(4, 5)))
    assertEquals(u, Uri(query = Query.unsafeFromString("param1=3&param2=4&param2=5")))
  }
  test("Uri parameter convenience methods should contains not a parameter") {
    assertEquals(Uri(query = Query.empty) ? "param1", false)
  }
  test("Uri parameter convenience methods should contains an empty parameter") {
    assertEquals(Uri(query = Query.unsafeFromString("")) ? "", true)
    assertEquals(Uri(query = Query.unsafeFromString("")) ? "param", false)
    assertEquals(Uri(query = Query.unsafeFromString("&&=value&&")) ? "", true)
    assertEquals(Uri(query = Query.unsafeFromString("&&=value&&")) ? "param", false)
  }
  test("Uri parameter convenience methods should contains a parameter") {
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value&param1=value")) ? "param1",
      true,
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value&param2=value")) ? "param2",
      true,
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value&param2=value")) ? "param3",
      false,
    )
  }
  test("Uri parameter convenience methods should contains a parameter with many values") {

    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param1=value3")) ? "param1",
      true,
    )
  }
  test("Uri parameter convenience methods should contains a parameter without a value") {
    assertEquals(Uri(query = Query.unsafeFromString("param1")) ? "param1", true)
  }
  test("Uri parameter convenience methods should contains with many parameters") {

    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2&=value3")) ? "param1",
      true,
    )

    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2&=value3")) ? "param2",
      true,
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2&=value3")) ? "",
      true,
    )
    assertEquals(
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2&=value3")) ? "param3",
      false,
    )
  }
  test("Uri parameter convenience methods should remove a parameter if present") {
    val u = Uri(query = Query.unsafeFromString("param1=value&param2=value")) -? "param1"
    assertEquals(u, Uri(query = Query.unsafeFromString("param2=value")))
  }
  test(
    "Uri parameter convenience methods should remove an empty parameter from an empty query string"
  ) {
    val u = Uri(query = Query.unsafeFromString("")) -? ""
    assertEquals(u, Uri(query = Query.empty))
  }
  test("Uri parameter convenience methods should remove nothing if parameter is not present") {
    val u = Uri(query = Query.unsafeFromString("param1=value&param2=value"))
    assertEquals(u -? "param3", u)
  }
  test("Uri parameter convenience methods should remove the last parameter") {
    val u = Uri(query = Query.unsafeFromString("param1=value")) -? "param1"
    assertEquals(u, Uri())
  }
  test("Uri parameter convenience methods should replace a parameter") {
    val u =
      Uri(query = Query.unsafeFromString("param1=value&param2=value")) +? ("param1" -> "newValue")
    assertEquals(
      u.multiParams,
      Uri(query = Query.unsafeFromString("param1=newValue&param2=value")).multiParams,
    )
  }
  test("Uri parameter convenience methods should replace a parameter without a value") {
    val u =
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2=value")) +? "param2"
    assertEquals(
      u.multiParams,
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2")).multiParams,
    )
  }
  test("Uri parameter convenience methods should replace the same parameter") {
    val u = Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2")) ++?
      ("param1" -> List("value1", "value2"))
    assertEquals(
      u.multiParams,
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2")).multiParams,
    )
  }
  test("Uri parameter convenience methods should replace the same parameter without a value") {
    val u = Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2")) +? "param2"
    assertEquals(
      u.multiParams,
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2&param2")).multiParams,
    )
  }
  test("Uri parameter convenience methods should replace a parameter set") {
    val u =
      Uri(query = Query.unsafeFromString("param1=value1&param1=value2")) +? ("param1" -> "value")
    assertEquals(u.multiParams, Uri(query = Query.unsafeFromString("param1=value")).multiParams)
  }
  test("Uri parameter convenience methods should set a parameter with a value") {
    val ps = Map("param" -> List("value"))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=value")))
  }
  test("Uri parameter convenience methods should set a parameter with a boolean values") {
    val ps = Map("param" -> List(true, false))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=true&param=false")))
  }
  test("Uri parameter convenience methods should set a parameter with a double values") {
    val ps = Map("param" -> List(1.2, 2.1))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=1.2&param=2.1")))
  }
  test("Uri parameter convenience methods should set a parameter with a float values") {
    assume(Platform.isJvm || Platform.isNative, "floats on JS have semantics of double")

    val ps = Map("param" -> List(1.2f, 2.1f))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=1.2&param=2.1")))
  }
  test("Uri parameter convenience methods should set a parameter with a integer values") {
    val ps = Map("param" -> List(1, 2, 3))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=1&param=2&param=3")))
  }
  test("Uri parameter convenience methods should set a parameter with a long values") {
    val ps = Map("param" -> List(Long.MaxValue, 0L, Long.MinValue))
    assertEquals(
      Uri() =? ps,
      Uri(
        query =
          Query.unsafeFromString("param=9223372036854775807&param=0&param=-9223372036854775808")
      ),
    )
  }
  test("Uri parameter convenience methods should set a parameter with a short values") {
    val ps = Map("param" -> List(Short.MaxValue, Short.MinValue))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=32767&param=-32768")))
  }
  test("Uri parameter convenience methods should set a parameter with a string values") {
    val ps = Map("param" -> List("some", "none"))
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param=some&param=none")))
  }
  test("Uri parameter convenience methods should set a parameter without a value") {
    val ps: Map[String, List[String]] = Map("param" -> Nil)
    assertEquals(Uri() =? ps, Uri(query = Query.unsafeFromString("param")))
  }
  test("Uri parameter convenience methods should set many parameters") {
    val ps = Map("param1" -> Nil, "param2" -> List("value1", "value2"), "param3" -> List("value"))
    assertEquals(
      Uri() =? ps,
      Uri(query = Query.unsafeFromString("param1&param2=value1&param2=value2&param3=value")),
    )
  }
  test("Uri parameter convenience methods should set the same parameters again") {
    val ps = Map("param" -> List("value"))
    val u = Uri(query = Query.unsafeFromString("param=value"))
    assertEquals(u =? ps, u =? ps)
  }
  test("Uri parameter convenience methods should discard the blank value in withQueryParam") {
    assertEquals(uri"/test?".withQueryParam("k", "v"), uri"/test?k=v")
  }
  test(
    "Uri parameter convenience methods should discard the blank value in withOptionQueryParam"
  ) {
    assertEquals(uri"/test?".withOptionQueryParam("k", Some("v")), uri"/test?k=v")
  }

  test("Uri.withFragment convenience method should set a Fragment") {
    val u = Uri(path = Uri.Path.Root)
    val updated = u.withFragment("nonsense")
    assertEquals(updated.renderString, "/#nonsense")
  }
  test("Uri.withFragment convenience method should set a new Fragment") {
    val u = Uri(path = Uri.Path.Root, fragment = Some("adjakda"))
    val updated = u.withFragment("nonsense")
    assertEquals(updated.renderString, "/#nonsense")
  }
  test("Uri.withFragment convenience method should set no Fragment on a null String") {
    val u = Uri(path = Uri.Path.Root, fragment = Some("adjakda"))
    val evilString: String = null
    val updated = u.withFragment(evilString)
    assertEquals(updated.renderString, "/")
  }

  test("Uri.withoutFragment convenience method should unset a Fragment") {
    val u = Uri(path = Uri.Path.Root, fragment = Some("nonsense"))
    val updated = u.withoutFragment
    assertEquals(updated.renderString, "/")
  }

  test("Uri.renderString should Encode special chars in the query") {
    val u = Uri(path = Uri.Path.Root).withQueryParam("foo", " !$&'()*+,;=:/?@~")
    assertEquals(u.renderString, "/?foo=%20%21%24%26%27%28%29%2A%2B%2C%3B%3D%3A/?%40~")
  }
  test("Uri.renderString should Encode special chars in the fragment") {
    val u = Uri(path = Uri.Path.Root, fragment = Some(" !$&'()*+,;=:/?@~"))
    assertEquals(u.renderString, "/#%20!$&'()*+,;=:/?@~")
  }

  val base = getUri("http://a/b/c/d;p?q")

  test("Uri relative resolution should correctly remove ./.. sequences") {
    implicit class checkDotSequences(path: Uri.Path) {
      def removingDotsShould_==(expected: Uri.Path): Unit =
        assertEquals(removeDotSegments(path), expected)
    }

    // from RFC 3986 sec 5.2.4
    path"mid/content=5/../6".removingDotsShould_==(path"mid/6")
    path"/a/b/c/./../../g".removingDotsShould_==(path"/a/g")
  }

  implicit class check(relative: String) {
    def shouldResolveTo(expected: String): Unit =
      assertEquals(base.resolve(getUri(relative)), getUri(expected))
  }

  test("Uri relative resolution should correctly resolve RFC 3986 sec 5.4 normal examples") {
    // normal examples
    "g:h" shouldResolveTo "g:h"
    "g" shouldResolveTo "http://a/b/c/g"
    "./g" shouldResolveTo "http://a/b/c/g"
    "g/" shouldResolveTo "http://a/b/c/g/"
    "/g" shouldResolveTo "http://a/g"
    "//g" shouldResolveTo "http://g"
    "?y" shouldResolveTo "http://a/b/c/d;p?y"
    "g?y" shouldResolveTo "http://a/b/c/g?y"
    "#s" shouldResolveTo "http://a/b/c/d;p?q#s"
    "g#s" shouldResolveTo "http://a/b/c/g#s"
    "g?y#s" shouldResolveTo "http://a/b/c/g?y#s"
    ";x" shouldResolveTo "http://a/b/c/;x"
    "g;x" shouldResolveTo "http://a/b/c/g;x"
    "g;x?y#s" shouldResolveTo "http://a/b/c/g;x?y#s"
    "" shouldResolveTo "http://a/b/c/d;p?q"
    "." shouldResolveTo "http://a/b/c/"
    "./" shouldResolveTo "http://a/b/c/"
    ".." shouldResolveTo "http://a/b/"
    "../" shouldResolveTo "http://a/b/"
    "../g" shouldResolveTo "http://a/b/g"
    "../.." shouldResolveTo "http://a/"
    "../../" shouldResolveTo "http://a/"
    "../../g" shouldResolveTo "http://a/g"
  }

  test("Uri relative resolution should correctly resolve RFC 3986 sec 5.4 abnormal examples") {
    "../../../g" shouldResolveTo "http://a/g"
    "../../../../g" shouldResolveTo "http://a/g"

    "/./g" shouldResolveTo "http://a/g"
    "/../g" shouldResolveTo "http://a/g"
    "g." shouldResolveTo "http://a/b/c/g."
    ".g" shouldResolveTo "http://a/b/c/.g"
    "g.." shouldResolveTo "http://a/b/c/g.."
    "..g" shouldResolveTo "http://a/b/c/..g"

    "./../g" shouldResolveTo "http://a/b/g"
    "./g/." shouldResolveTo "http://a/b/c/g/"
    "g/./h" shouldResolveTo "http://a/b/c/g/h"
    "g/../h" shouldResolveTo "http://a/b/c/h"
    "g;x=1/./y" shouldResolveTo "http://a/b/c/g;x=1/y"
    "g;x=1/../y" shouldResolveTo "http://a/b/c/y"

    "g?y/./x" shouldResolveTo "http://a/b/c/g?y/./x"
    "g?y/../x" shouldResolveTo "http://a/b/c/g?y/../x"
    "g#s/./x" shouldResolveTo "http://a/b/c/g#s/./x"
    "g#s/../x" shouldResolveTo "http://a/b/c/g#s/../x"

    "http:g" shouldResolveTo "http:g"
  }

  lazy val pathSegmentGen: Gen[String] =
    Gen.oneOf(Gen.alphaNumStr, Gen.const("."), Gen.const(".."))

  lazy val pathGen: Gen[String] =
    for {
      firstPathSegment <- Gen.oneOf(Gen.const(""), pathSegmentGen)
      pathSegments <- Gen.listOf(pathSegmentGen.map(p => s"/$p"))
      lastSlash <- Gen.oneOf("", "/")
    } yield s"$firstPathSegment${pathSegments.mkString("")}$lastSlash"

  test("Uri relative resolution should correctly remove dot segments in other examples") {
    forAll(pathGen) { (input: String) =>
      val prefix = "/this/isa/prefix/"
      val processed = Uri.removeDotSegments(Uri.Path.unsafeFromString(input)).renderString
      val path = (fs2.io.file.Path(prefix) / processed).normalize
      assert(path.startsWith(fs2.io.file.Path(prefix)))
      assert(!processed.contains("./"))
      assert(!processed.contains("../"))
    }
  }

  test("Uri.addPath should add urlencoded segments to uri") {
    val uri = getUri("http://localhost/foo")
    assertEquals(
      uri.addPath("more/path/auth|urlencoded"),
      getUri("http://localhost/foo/more/path/auth%7Curlencoded"),
    )
  }

  test("Uri.equals should be false between an empty path and a trailing slash after an authority") {
    assertNotEquals(uri"http://example.com", uri"http://example.com/")
  }

  test("/ should encode space as %20") {
    assertEquals(uri"http://example.com/" / " ", uri"http://example.com/%20")
  }

  test("/ should encode generic delimiters that aren't pchars") {
    // ":" and "@" are valid pchars
    assertEquals(uri"http://example.com" / ":/?#[]@", uri"http://example.com/:%2F%3F%23%5B%5D@")
  }

  test("/ should encode percent sequences") {
    assertEquals(uri"http://example.com" / "%2F", uri"http://example.com/%252F")
  }

  test("/ should not encode sub-delims") {
    assertEquals(uri"http://example.com" / "!$&'()*+,;=", uri"http://example.com/!$$&'()*+,;=")
  }

  test("/ should UTF-8 encode characters") {
    assertEquals(uri"http://example.com/" / "ö", uri"http://example.com/%C3%B6")
  }

  test("/ should not make bad URIs") {
    forAll { (s: String) =>
      Uri.fromString((uri"http://example.com/" / s).toString).isRight
    }
  }

  test("Encoding a URI should not change any of the allowed chars") {
    val encoded =
      encode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*+,;=:/?@-._~")
    assertEquals(
      encoded,
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*+,;=:/?@-._~",
    )
  }
  test(
    "Encoding a URI should not uppercase hex digits after percent chars that will be encoded"
  ) {
    // https://github.com/http4s/http4s/issues/720
    assertEquals(encode("hello%3fworld"), "hello%253fworld")
  }
  test("Encoding a URI should percent encode spaces") {
    assertEquals(encode("hello world"), "hello%20world")
  }
  test("Encoding a URI should encode a letter with an accent as 2 values") {
    assertEquals(encode("é"), "%C3%A9")
  }

  test("Decoding a URI should not change any of the allowed chars") {
    val decoded = decode(
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*,;=:/?#[]@-._~"
    )
    assertEquals(
      decoded,
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*,;=:/?#[]@-._~",
    )
  }
  test("Decoding a URI should leave Fußgängerübergänge as is") {
    assertEquals(decode("Fußgängerübergänge"), "Fußgängerübergänge")
  }
  test("Decoding a URI should not overflow on all utf-8 chars") {
    assertEquals(decode("äéèüああああああああ"), "äéèüああああああああ")
  }
  test("Decoding a URI should decode a pct encoded string") {
    assertEquals(decode("hello%20world"), "hello world")
  }
  test("Decoding a URI should gracefully handle '%' encoding errors") {
    assertEquals(decode("%"), "%")
    assertEquals(decode("%2"), "%2")
    assertEquals(decode("%20"), " ")
  }
  test("Decoding a URI should decode value consisting of 2 values to 1 char") {
    assertEquals(decode("%C3%A9"), "é")
  }
  test("Decoding a URI should skip the chars in toSkip when decoding") {
    test("skips '%2F' when decoding") {
      assertEquals(decode("%2F", toSkip = CharPredicate("/?#")), "%2F")
    }
    test("skips '%23' when decoding") {
      assertEquals(decode("%23", toSkip = CharPredicate("/?#")), "%23")
    }
    test("skips '%3F' when decoding") {
      assertEquals(decode("%3F", toSkip = CharPredicate("/?#")), "%3F")
    }
  }
  test("Decoding a URI should still encodes others") {
    assertEquals(decode("br%C3%BCcke", toSkip = CharPredicate("/?#")), "brücke")
  }
  test("Decoding a URI should handles mixed") {
    assertEquals(
      decode("/ac%2Fdc/br%C3%BCcke%2342%3Fcheck", toSkip = CharPredicate("/?#")),
      "/ac%2Fdc/brücke%2342%3Fcheck",
    )
  }

  test(
    "The plusIsSpace flag should treats + as allowed when the plusIsSpace flag is either not supplied or supplied as false"
  ) {
    assertEquals(decode("+"), "+")
    assertEquals(decode("+", plusIsSpace = false), "+")
  }
  test("The plusIsSpace flag should decode + as space when the plusIsSpace flag is true") {
    assertEquals(decode("+", plusIsSpace = true), " ")
  }

  test("urlDecode(urlEncode(s)) == s should for all s") {
    forAll { (s: String) =>
      assertEquals(decode(encode(s)), s)
    }
  }
  test("""urlDecode(urlEncode(s)) == s should for "%ab"""") {
    // Special case that triggers https://github.com/http4s/http4s/issues/720,
    // not likely to be uncovered by the generator.
    assertEquals(decode(encode("%ab")), "%ab")
  }
  test("""urlDecode(urlEncode(s)) == s should when decode skips a skipped percent encoding""") {
    // This is a silly thing to do, but as long as the API allows it, it would
    // be good to know if it breaks.
    assertEquals(
      decode(encode("%2f", toSkip = CharPredicate("%")), toSkip = CharPredicate("/")),
      "%2f",
    )
  }

  test("Uri.Path should check that we store the encoded path from parsed") {
    val uriReference = uri"https://example.com/auth0%7Cdsfhsklh46ksx/we-have-a%2Ftest"
    assertEquals(
      uriReference.path.segments,
      Vector("auth0%7Cdsfhsklh46ksx", "we-have-a%2Ftest").map(Uri.Path.Segment.encoded),
    )
  }
  test("Uri.Path should check that we store the encoded ") {
    val uriReference = uri"https://example.com/test" / "auth0|dsfhsklh46ksx" / "we-have-a/test"
    assertEquals(
      uriReference.path.segments,
      Vector("test", "auth0%7Cdsfhsklh46ksx", "we-have-a%2Ftest")
        .map(Uri.Path.Segment.encoded),
    )
  }

  test("Uri.Path should indexOf / Split") {
    val path1 = path"/foo/bar/baz"
    val path2 = path"/foo"
    val split = path1.findSplit(path2)
    assertEquals(split, Some(1))
    val (pre, post) = path1.splitAt(split.getOrElse(0))
    assertEquals(pre, path2)
    assertEquals(post, path"/bar/baz")
    assertEquals(pre.concat(post), path1)
  }

  test("empty concat 1")(assertEquals(path"".concat(path""), path""))
  test("empty concat 2")(assertEquals(path"/".concat(path""), path"/"))
  test("empty concat 3")(assertEquals(path"".concat(path"/"), path"/"))
  test("empty concat 4")(assertEquals(path"/".concat(path"/"), path"/"))

  test("simple concat 1")(assertEquals(path"a/".concat(path""), path"a/"))
  test("simple concat 2")(assertEquals(path"a".concat(path"/"), path"a/"))
  test("simple concat 3")(assertEquals(path"".concat(path"/a"), path"/a"))
  test("simple concat 4")(assertEquals(path"/".concat(path"a"), path"/a"))

  // Please keep in mind that this property doesn't go the other way around.
  // The result being absolute does not imply the left side being absolute. See "simple concat 3".
  property("When the left side of concat is absolute then the result is absolute") {
    forAll((left: Path, right: Path) => assert(left.toAbsolute.concat(right).absolute))
  }

  // Please keep in mind that this property doesn't go the other way around.
  // The result ending with slash does not imply the right side ending with slash. See "simple concat 1".
  property("When the right side of concat ends with slash then the result ends with slash") {
    forAll((left: Path, right: Path) => assert(left.concat(right.addEndsWithSlash).endsWithSlash))
  }

  property("size of concat is sum of sizes") {
    forAll((left: Path, right: Path) =>
      assertEquals(left.concat(right).segments.size, left.segments.size + right.segments.size)
    )
  }

  test("splitAt -1")(assertEquals(path"/a/b/".splitAt(-1), (path"", path"/a/b/")))
  test("splitAt 0")(assertEquals(path"/a/b/".splitAt(0), (path"", path"/a/b/")))
  test("splitAt in the middle")(assertEquals(path"/a/b/".splitAt(1), (path"/a", path"/b/")))
  test("splitAt segments.size")(assertEquals(path"/a/b/".splitAt(2), (path"/a/b", path"/")))
  test("splitAt segments.size + 1")(assertEquals(path"/a/b/".splitAt(3), (path"/a/b/", path"")))

  property("splitAt(0)._2 is identity") {
    forAll((path: Path) => assertEquals(path.splitAt(0)._2, path))
  }

  property("splitAt(-1) equals splitAt(0)") {
    forAll((path: Path) => assertEquals(path.splitAt(-1), path.splitAt(0)))
  }

  property("splitAt(segments.size + 1)._1 is identity") {
    forAll((path: Path) => assertEquals(path.splitAt(path.segments.size + 1)._1, path))
  }

  property("splitAt followed by concat is identity") {
    forAll { (path: Path, index: Int) =>
      val (l, r) = path.splitAt(index)
      assertEquals(l.concat(r), path)
    }
  }

  // Additional, heavier testing for the case of splitAt(0)
  property("splitAt(0) followed by concat is identity") {
    forAll { (path: Path) =>
      val (l, r) = path.splitAt(0)
      assertEquals(l.concat(r), path)
    }
  }

  // Additional, heavier testing for the case of splitAt(1)
  property("splitAt(1) followed by concat is identity") {
    forAll { (path: Path) =>
      val (l, r) = path.splitAt(1)
      assertEquals(l.concat(r), path)
    }
  }

  test("toOriginForm strips scheme and authority") {
    uri"http://example.com/foo?q".toOriginForm == uri"/foo?q"
  }

  test("toOriginForm strips fragment") {
    uri"/foo?q#top".toOriginForm == uri"/foo?q"
  }

  test("toOriginForm infers an empty path") {
    uri"http://example.com".toOriginForm == uri"/"
  }

  test("toOriginForm infers paths relative to root") {
    uri"dubious".toOriginForm == uri"/dubious"
  }

  test("Use lazy query model parsing in uri parsing") {
    val ori = "http://domain.com/path?param1=asd;fgh"
    val res = org.http4s.Uri.unsafeFromString(ori).renderString
    assertEquals(ori, res)
  }

  property("resolving root sets path to root") {
    forAll { (uri: Uri) =>
      assertEquals(uri.resolve(uri"/").path, Uri.Path.Root)
    }
  }

  checkAll("Uri.Path", SemigroupTests[Uri.Path].semigroup)
  checkAll("Uri.Path", EqTests[Uri.Path].eqv)

  checkAll("Eq[Uri]", EqTests[Uri].eqv)
  checkAll("Order[Uri]", OrderTests[Uri].order)
  checkAll("Hash[Uri]", HashTests[Uri].hash)
}
