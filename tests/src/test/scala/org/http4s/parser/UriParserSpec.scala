package org.http4s.parser

import cats.implicits._
import java.nio.charset.{StandardCharsets, Charset => NioCharset}

import org.http4s.Uri.Scheme.https
import org.http4s._
import org.http4s.Uri._
import org.http4s.internal.parboiled2._

class IpParserImpl(val input: ParserInput, val charset: NioCharset) extends Parser with IpParser {
  def CaptureIPv6: Rule1[String] = rule { capture(IpV6Address) }
  def CaptureIPv4: Rule1[String] = rule { capture(IpV4Address) }
}

class UriParserSpec extends Http4sSpec {
  "Uri.requestTarget" should {
    def check(items: Seq[(String, Uri)]) = foreach(items) {
      case (str, uri) =>
        Uri.requestTarget(str) must beRight(uri)
    }

    // RFC 3986 examples
    // http://tools.ietf.org/html/rfc3986#section-1.1.2

    // http://www.ietf.org/rfc/rfc2396.txt

    "parse a IPv6 address" in {
      val v = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield f + "::" + b)

      foreach(v) { s =>
        Either.fromTry(new IpParserImpl(s, StandardCharsets.UTF_8).CaptureIPv6.run()) must beRight(
          s)
      }
    }

    "parse a IPv4 address" in {
      foreach(0 to 255) { i =>
        val addr = s"$i.$i.$i.$i"
        Either.fromTry(new IpParserImpl(addr, StandardCharsets.UTF_8).CaptureIPv4.run()) must beRight(
          addr)
      }
    }

    "parse a short IPv6 address in brackets" in {
      val s = "[01ab::32ba:32ba]"
      Uri.requestTarget(s) must beRight(
        Uri(authority = Some(Authority(host = ipv6"01ab::32ba:32ba"))))
    }

    "handle port configurations" in {
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

    "parse absolute URIs" in {
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

    "parse relative URIs" in {
      val relativeUris: Seq[(String, Uri)] = Seq(
        ("/foo/bar", Uri(path = "/foo/bar")),
        (
          "/foo/bar?foo=bar&ding=dong",
          Uri(path = "/foo/bar", query = Query.fromPairs("foo" -> "bar", "ding" -> "dong"))),
        ("/", Uri(path = "/"))
      )

      check(relativeUris)
    }

    "parse absolute URI with fragment" in {
      val u = Uri.requestTarget("http://foo.bar/foo#Examples")
      u must beRight(
        Uri(
          Some(Scheme.http),
          Some(Authority(host = RegName("foo.bar".ci))),
          "/foo",
          Query.empty,
          Some("Examples")))
    }

    "parse absolute URI with parameters and fragment" in {
      val u = Uri.requestTarget("http://foo.bar/foo?bar=baz#Example-Fragment")
      u must beRight(
        Uri(
          Some(Scheme.http),
          Some(Authority(host = RegName("foo.bar".ci))),
          "/foo",
          Query.fromPairs("bar" -> "baz"),
          Some("Example-Fragment")))
    }

    "parse relative URI with empty query string" in {
      val u = Uri.requestTarget("/foo/bar?")
      u must beRight(Uri(path = "/foo/bar", query = Query("" -> None)))
    }

    "parse relative URI with empty query string followed by empty fragment" in {
      val u = Uri.requestTarget("/foo/bar?#")
      u must beRight(Uri(path = "/foo/bar", query = Query("" -> None), fragment = Some("")))
    }

    "parse relative URI with empty query string followed by fragment" in {
      val u = Uri.requestTarget("/foo/bar?#Example_of_Fragment")
      u must beRight(
        Uri(path = "/foo/bar", query = Query("" -> None), fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with fragment" in {
      val u = Uri.requestTarget("/foo/bar#Examples_of_Fragment")
      u must beRight(Uri(path = "/foo/bar", fragment = Some("Examples_of_Fragment")))
    }

    "parse relative URI with single parameter without a value followed by a fragment" in {
      val u = Uri.requestTarget("/foo/bar?bar#Example_of_Fragment")
      u must beRight(
        Uri(
          path = "/foo/bar",
          query = Query("bar" -> None),
          fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with parameters and fragment" in {
      val u = Uri.requestTarget("/foo/bar?bar=baz#Example_of_Fragment")
      u must beRight(
        Uri(
          path = "/foo/bar",
          query = Query.fromPairs("bar" -> "baz"),
          fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with slash and fragment" in {
      val u = Uri.requestTarget("/#Example_Fragment")
      u must beRight(Uri(path = "/", fragment = Some("Example_Fragment")))
    }

    {
      val q = Query.fromString("param1=3&param2=2&param2=foo")
      val u = Uri(query = q)
      "represent query as multiParams as a Map[String,Seq[String]]" in {
        u.multiParams must be_==(Map("param1" -> Seq("3"), "param2" -> Seq("2", "foo")))
      }

      "parse query and represent params as a Map[String,String] taking the first param" in {
        u.params must be_==(Map("param1" -> "3", "param2" -> "2"))
      }
    }

    "fail on invalid uri" in {
      val invalid = Seq("^", "]", "/hello/wo%2rld", "/hello/world?bad=enc%ode")
      forall(invalid) { _ =>
        Uri.fromString("^") must beLeft
        Uri.requestTarget("^") must beLeft
      }
    }
  }

  "Uri.fromString" should {
    def check(items: Seq[(String, Uri)]) = foreach(items) {
      case (str, uri) =>
        Uri.fromString(str) must beRight(uri)
    }

    "parse absolute URIs" in {
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

    "parse a path-noscheme uri" in {
      Uri.fromString("q") must beRight.like {
        case u =>
          u must_== Uri(path = "q")
      }
      Uri.fromString("a/b") must beRight.like {
        case u =>
          u must_== Uri(path = "a/b")
      }
    }

    "parse a path-noscheme uri with query" in {
      Uri.fromString("a/b?foo") must beRight.like {
        case u =>
          u must_== Uri(path = "a/b", query = Query(("foo", None)))
      }
    }

    "parse a path-absolute uri" in {
      Uri.fromString("/a/b") must beRight.like {
        case u =>
          u must_== Uri(path = "/a/b")
      }
    }
    "parse a path-absolute uri with query" in {
      Uri.fromString("/a/b?foo") must beRight.like {
        case u =>
          u must_== Uri(path = "/a/b", query = Query(("foo", None)))
      }
    }
    "parse a path-absolute uri with query and fragment" in {
      Uri.fromString("/a/b?foo#bar") must beRight.like {
        case u =>
          u must_== Uri(path = "/a/b", query = Query(("foo", None)), fragment = Some("bar"))
      }
    }
  }

  "String interpolator" should {
    "parse valid URIs" in {
      uri"https://http4s.org" must_== Uri(
        scheme = Option(https),
        authority = Option(Uri.Authority(host = RegName("http4s.org".ci))))
    }

    "reject invalid URIs" in {
      import org.specs2.execute._, Typecheck._
      import org.specs2.matcher.TypecheckMatchers._

      typecheck {
        """
           uri"not valid"
        """
      } must not succeed
    }
  }
}
