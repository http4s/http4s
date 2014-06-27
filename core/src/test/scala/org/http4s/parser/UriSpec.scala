package org.http4s.parser

import java.nio.charset.Charset

import org.http4s.Uri._
import org.http4s.util.string._
import org.http4s.{ CharacterSet, Uri }
import org.scalatest.{ Matchers, WordSpec }

import scala.util.Success
import org.parboiled2._

/**
 * Created by Bryce Anderson on 3/18/14.
 */
class IPV6Parser(val input: ParserInput, val charset: Charset) extends Parser with Rfc3986Parser {
  def CaptureIPv6: Rule1[String] = rule { capture(IpV6Address) }
}

class UriSpec extends WordSpec with Matchers {

  "Uri" should {

    // RFC 3986 examples
    // http://tools.ietf.org/html/rfc3986#section-1.1.2

    // http://www.ietf.org/rfc/rfc2396.txt

    "parse a IPv6 address" in {

      val v = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield (f + "::" + b))

      v.foreach { s =>
        new IPV6Parser(s, CharacterSet.`UTF-8`.charset).CaptureIPv6.run() should equal(Success((s)))
      }
    }

    "parse a short IPv6 address" in {
      val s = "01ab::32ba:32ba"
      Uri.fromString("01ab::32ba:32ba").get should equal(Uri(authority = Some(Authority(host = IPv6("01ab::32ba:32ba")))))
    }

    "handle port configurations" in {
      val portExamples: Seq[(String, Uri)] = Seq(
        ("http://foo.com", Uri(Some("http".ci), Some(Authority(host = RegName("foo.com".ci), port = None)))),
        ("http://foo.com:", Uri(Some("http".ci), Some(Authority(host = RegName("foo.com".ci), port = None)))),
        ("http://foo.com:80", Uri(Some("http".ci), Some(Authority(host = RegName("foo.com".ci), port = Some(80))))))

      check(portExamples)
    }

    "parse absolute URIs" in {
      val absoluteUris: Seq[(String, Uri)] = Seq(
        ("http://www.foo.com", Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci))))),
        ("http://www.foo.com/foo?bar=baz",
          Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci))), "/foo", Some("bar=baz"))),
        ("http://192.168.1.1",
          Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci))))),
        ("http://192.168.1.1:80/c?GB=object&Class=one",
          Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(80))), "/c", Some("GB=object&Class=one"))),
        ("http://[2001:db8::7]/c?GB=object&Class=one",
          Uri(Some("http".ci), Some(Authority(host = IPv6("2001:db8::7".ci))), "/c", Some("GB=object&Class=one"))),
        ("mailto:John.Doe@example.com",
          Uri(Some("mailto".ci), path = "John.Doe@example.com")))

      check(absoluteUris)
    }

    "parse relative URIs" in {
      val relativeUris: Seq[(String, Uri)] = Seq(
        ("/foo/bar", Uri(path = "/foo/bar")),
        ("/foo/bar?foo=bar&ding=dong", Uri(path = "/foo/bar", query = Some("foo=bar&ding=dong"))),
        ("/", Uri()))

      check(relativeUris)
    }

    "parse absolute URI with fragment" in {
      val u = Uri.fromString("http://foo.bar/foo#Examples").get
      u should equal(Uri(Some("http".ci), Some(Authority(host = RegName("foo.bar".ci))), "/foo", None, Some("Examples")))
    }

    "parse absolute URI with parameters and fragment" in {
      val u = Uri.fromString("http://foo.bar/foo?bar=baz#Example-Fragment").get
      u should equal(Uri(Some("http".ci), Some(Authority(host = RegName("foo.bar".ci))), "/foo", Some("bar=baz"), Some("Example-Fragment")))
    }

    "parse relative URI with fragment" in {
      val u = Uri.fromString("/foo/bar#Examples_of_Fragment").get
      u should equal(Uri(path = "/foo/bar", fragment = Some("Examples_of_Fragment")))
    }

    "parse relative URI with single parameter without a value followed by a fragment" in {
      val u = Uri.fromString("/foo/bar?bar#Example_of_Fragment").get
      u should equal(Uri(path = "/foo/bar", query = Some("bar"), fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with parameters and fragment" in {
      val u = Uri.fromString("/foo/bar?bar=baz#Example_of_Fragment").get
      u should equal(Uri(path = "/foo/bar", query = Some("bar=baz"), fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with slash and fragment" in {
      val u = Uri.fromString("/#Example_Fragment").get
      u should equal(Uri(path = "/", fragment = Some("Example_Fragment")))
    }

    {
      val q = "param1=3&param2=2&param2=foo"
      val u = Uri(query = Some(q))
      "parse query and represent multiParams as a Map[String,Seq[String]]" in {
        u.multiParams should equal(Map("param1" -> Seq("3"), "param2" -> Seq("2", "foo")))
      }

      "parse query and represent params as a Map[String,String] taking the first param" in {
        u.params should equal(Map("param1" -> "3", "param2" -> "2"))
      }
    }

    "deal with an invalid Query" in {
      val u = Uri.fromString("/hello/world?bad=enc%ode").get
      u.params should equal(Map("bad" -> "enc"))
      u.fragment should equal(None)
      u.path should equal("/hello/world")
    }

    "deal with an invalid Uri" in {
      val u = Uri.fromString("/hello/wo%2rld").get
      u.path should equal("/hello/wo")
    }

    def check(items: Seq[(String, Uri)]) = items.foreach {
      case (str, uri) =>
        Uri.fromString(str).get should equal(uri)
    }

  }

  "Uri to String" should {

    "render default URI" in {
      Uri().toString should equal("/")
    }

    "render a IPv6 address, should be wrapped in brackets" in {
      val variants = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield (f + "::" + b))

      for (s <- variants) {
        Uri(Some("http".ci), Some(Authority(host = IPv6(s.ci))), "/foo", Some("bar=baz")).toString should
          equal(s"http://[$s]/foo?bar=baz")
      }
    }

    "render URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci))), "/foo", Some("bar=baz")).toString should
        equal("http://www.foo.com/foo?bar=baz")
    }

    "render URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci), port = Some(80)))).toString should
        equal("http://www.foo.com:80")
    }

    "render URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci)))).toString should
        equal("http://www.foo.com")
    }

    "render IPv4 URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(80))), "/c", Some("GB=object&Class=one")).toString should
        equal("http://192.168.1.1:80/c?GB=object&Class=one")
    }

    "render IPv4 URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(8080)))).toString should
        equal("http://192.168.1.1:8080")
    }

    "render IPv4 URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci)))).toString should
        equal("http://192.168.1.1")
    }

    "render IPv6 URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:db8::7".ci))), "/c", Some("GB=object&Class=one")).toString should
        equal("http://[2001:db8::7]/c?GB=object&Class=one")
    }

    "render IPv6 URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci), port = Some(8080)))).toString should
        equal("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]:8080")
    }

    "render IPv6 URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci)))).toString should
        equal("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]")
    }

    "render email address" in {
      Uri(Some("mailto".ci), path = "John.Doe@example.com").toString should
        equal("mailto:John.Doe@example.com")
    }

    "render an URL with username and password" in {
      Uri(Some("http".ci), Some(Authority(Some("username:password"), RegName("some.example.com"), None)), "/", None, None).toString should
        equal("http://username:password@some.example.com")
    }

    "render an URL with username and password, path and params" in {
      Uri(Some("http".ci), Some(Authority(Some("username:password"), RegName("some.example.com"), None)), "/some/path", Some("param1=5&param-without-value"), None).toString should
        equal("http://username:password@some.example.com/some/path?param1=5&param-without-value")
    }

    "render relative path with fragment" in {
      Uri(path = "/foo/bar", fragment = Some("an-anchor")).toString should
        equal("/foo/bar#an-anchor")
    }

    "render relative path with parameters" in {
      Uri(path = "/foo/bar", query = Some("foo=bar&ding=dong")).toString should
        equal("/foo/bar?foo=bar&ding=dong")
    }

    "render relative path with parameters and fragment" in {
      Uri(path = "/foo/bar", query = Some("foo=bar&ding=dong"), fragment = Some("an_anchor")).toString should
        equal("/foo/bar?foo=bar&ding=dong#an_anchor")
    }

    "render relative path without parameters" in {
      Uri(path = "/foo/bar").toString should
        equal("/foo/bar")
    }

    "render relative root path without parameters" in {
      Uri(path = "/").toString should
        equal("/")
    }

    "render a query string with a single param" in {
      Uri(query = Some("param1=test")).toString should
        equal("/?param1=test")
    }

    "render a query string with multiple value in a param" in {
      Uri(query = Some("param1=3&param2=2&param2=foo")).toString should
        equal("/?param1=3&param2=2&param2=foo")
    }

    "round trip over URI examples from wikipedia" in {
      /* 
       * Examples from:
       * - http://de.wikipedia.org/wiki/Uniform_Resource_Identifier
       * - http://en.wikipedia.org/wiki/Uniform_Resource_Identifier
       * 
       * URI.fromString fails for:
       * - "http://en.wikipedia.org/wiki/URI#Examples_of_URI_references",
       * - "file:///C:/Users/Benutzer/Desktop/Uniform%20Resource%20Identifier.html",
       * - "file:///etc/fstab",
       * - "relative/path/to/resource.txt",
       * - "//example.org/scheme-relative/URI/with/absolute/path/to/resource.txt",
       * - "../../../resource.txt",
       * - "./resource.txt#frag01",
       * - "resource.txt",
       * - "#frag01",
       * - ""
       * 
       */
      val examples = Seq(
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
        "/relative/URI/with/absolute/path/to/resource.txt")
      for (e <- examples) {
        Uri.fromString(e).get.toString should equal(e)
      }
    }

  }

  "Uri parameters" should {
    "parse empty query string" in {
      Uri(query = Some("")).multiParams should equal(Map.empty)
    }
    "parse parameter without key but with empty value" in {
      Uri(query = Some("=")).multiParams should equal(Map("" -> List("")))
    }
    "parse parameter without key but with value" in {
      Uri(query = Some("=value")).multiParams should equal(Map("" -> List("value")))
    }
    "parse single parameter with empty value" in {
      Uri(query = Some("param1=")).multiParams should equal(Map("param1" -> List("")))
    }
    "parse single parameter with value" in {
      Uri(query = Some("param1=value")).multiParams should equal(Map("param1" -> List("value")))
    }
    "parse single parameter without value" in {
      Uri(query = Some("param1")).multiParams should equal(Map("param1" -> Nil))
    }
    "parse many parameter with value" in {
      Uri(query = Some("param1=value&param2=value1&param2=value2&param3=value")).multiParams should
        equal(Map(
          "param1" -> List("value"),
          "param2" -> List("value1", "value2"),
          "param3" -> List("value")))
    }
    "parse many parameter without value" in {
      Uri(query = Some("param1&param2&param3")).multiParams should
        equal(Map(
          "param1" -> Nil,
          "param2" -> Nil,
          "param3" -> Nil))
    }
  }

}
