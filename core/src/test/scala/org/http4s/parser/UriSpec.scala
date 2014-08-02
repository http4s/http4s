package org.http4s.parser

import java.nio.charset.{Charset => NioCharset, StandardCharsets}

import org.http4s.Uri._
import org.http4s.util.string._
import org.http4s.{ Charset, Uri }
import org.specs2.mutable.Specification

import scala.util.Success
import org.parboiled2._

class IPV6Parser(val input: ParserInput, val charset: NioCharset) extends Parser with Rfc3986Parser {
  def CaptureIPv6: Rule1[String] = rule { capture(IpV6Address) }
}

class UriSpec extends Specification {

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

      foreach(v) { s =>
        new IPV6Parser(s, StandardCharsets.UTF_8).CaptureIPv6.run() must be_==(Success((s)))
      }
    }

    "parse a short IPv6 address" in {
      val s = "01ab::32ba:32ba"
      Uri.fromString("01ab::32ba:32ba").get must be_==(Uri(authority = Some(Authority(host = IPv6("01ab::32ba:32ba")))))
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
      u must be_==(Uri(Some("http".ci), Some(Authority(host = RegName("foo.bar".ci))), "/foo", None, Some("Examples")))
    }

    "parse absolute URI with parameters and fragment" in {
      val u = Uri.fromString("http://foo.bar/foo?bar=baz#Example-Fragment").get
      u must be_==(Uri(Some("http".ci), Some(Authority(host = RegName("foo.bar".ci))), "/foo", Some("bar=baz"), Some("Example-Fragment")))
    }

    "parse relative URI with empty query string" in {
      val u = Uri.fromString("/foo/bar?").get
      u must be_==(Uri(path = "/foo/bar", query = Some("")))
    }

    "parse relative URI with empty query string followed by empty fragement" in {
      val u = Uri.fromString("/foo/bar?#").get
      u must be_==(Uri(path = "/foo/bar", query = Some(""), fragment = Some("")))
    }

    "parse relative URI with empty query string followed by fragement" in {
      val u = Uri.fromString("/foo/bar?#Example_of_Fragment").get
      u must be_==(Uri(path = "/foo/bar", query = Some(""), fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with fragment" in {
      val u = Uri.fromString("/foo/bar#Examples_of_Fragment").get
      u must be_==(Uri(path = "/foo/bar", fragment = Some("Examples_of_Fragment")))
    }

    "parse relative URI with single parameter without a value followed by a fragment" in {
      val u = Uri.fromString("/foo/bar?bar#Example_of_Fragment").get
      u must be_==(Uri(path = "/foo/bar", query = Some("bar"), fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with parameters and fragment" in {
      val u = Uri.fromString("/foo/bar?bar=baz#Example_of_Fragment").get
      u must be_==(Uri(path = "/foo/bar", query = Some("bar=baz"), fragment = Some("Example_of_Fragment")))
    }

    "parse relative URI with slash and fragment" in {
      val u = Uri.fromString("/#Example_Fragment").get
      u must be_==(Uri(path = "/", fragment = Some("Example_Fragment")))
    }

    {
      val q = "param1=3&param2=2&param2=foo"
      val u = Uri(query = Some(q))
      "parse query and represent multiParams as a Map[String,Seq[String]]" in {
        u.multiParams must be_==(Map("param1" -> Seq("3"), "param2" -> Seq("2", "foo")))
      }

      "parse query and represent params as a Map[String,String] taking the first param" in {
        u.params must be_==(Map("param1" -> "3", "param2" -> "2"))
      }
    }

    "deal with an invalid Query" in {
      val u = Uri.fromString("/hello/world?bad=enc%ode").get
      u.params must be_==(Map("bad" -> "enc"))
      u.fragment must be_==(None)
      u.path must be_==("/hello/world")
    }

    "deal with an invalid Uri" in {
      val u = Uri.fromString("/hello/wo%2rld").get
      u.path must be_==("/hello/wo")
    }

    def check(items: Seq[(String, Uri)]) = foreach(items) {
      case (str, uri) =>
        Uri.fromString(str).get must be_==(uri)
    }

  }

  "Uri to String" should {

    "render default URI" in {
      Uri().toString must be_==("/")
    }

    "render a IPv6 address, should be wrapped in brackets" in {
      val variants = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield (f + "::" + b))

      foreach (variants) { s =>
        Uri(Some("http".ci), Some(Authority(host = IPv6(s.ci))), "/foo", Some("bar=baz")).toString must_==
          (s"http://[$s]/foo?bar=baz")
      }
    }

    "render URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci))), "/foo", Some("bar=baz")).toString must_==("http://www.foo.com/foo?bar=baz")
    }

    "render URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci), port = Some(80)))).toString must_==("http://www.foo.com:80")
    }

    "render URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci)))).toString must_==("http://www.foo.com")
    }

    "render IPv4 URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(80))), "/c", Some("GB=object&Class=one")).toString must_==("http://192.168.1.1:80/c?GB=object&Class=one")
    }

    "render IPv4 URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(8080)))).toString must_==("http://192.168.1.1:8080")
    }

    "render IPv4 URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci)))).toString must_==("http://192.168.1.1")
    }

    "render IPv6 URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:db8::7".ci))), "/c", Some("GB=object&Class=one")).toString must_==("http://[2001:db8::7]/c?GB=object&Class=one")
    }

    "render IPv6 URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci), port = Some(8080)))).toString must_==("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]:8080")
    }

    "render IPv6 URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci)))).toString must_==("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]")
    }

    "render email address" in {
      Uri(Some("mailto".ci), path = "John.Doe@example.com").toString must_==("mailto:John.Doe@example.com")
    }

    "render an URL with username and password" in {
      Uri(Some("http".ci), Some(Authority(Some("username:password"), RegName("some.example.com"), None)), "/", None, None).toString must_==("http://username:password@some.example.com")
    }

    "render an URL with username and password, path and params" in {
      Uri(Some("http".ci), Some(Authority(Some("username:password"), RegName("some.example.com"), None)), "/some/path", Some("param1=5&param-without-value"), None).toString  must_==("http://username:password@some.example.com/some/path?param1=5&param-without-value")
    }

    "render relative URI with empty query string" in {
      Uri(path = "/", query = Some(""), fragment = None).toString must_==("/?")
    }

    "render relative URI with empty query string and fragment" in {
      Uri(path = "/", query = Some(""), fragment = Some("")).toString must_==("/?#")
    }

    "render relative URI with empty fragment" in {
      Uri(path = "/", query = None, fragment = Some("")).toString must_== ("/#")
    }

    "render relative path with fragment" in {
      Uri(path = "/foo/bar", fragment = Some("an-anchor")).toString must_==("/foo/bar#an-anchor")
    }

    "render relative path with parameters" in {
      Uri(path = "/foo/bar", query = Some("foo=bar&ding=dong")).toString must_==("/foo/bar?foo=bar&ding=dong")
    }

    "render relative path with parameters and fragment" in {
      Uri(path = "/foo/bar", query = Some("foo=bar&ding=dong"), fragment = Some("an_anchor")).toString must_==("/foo/bar?foo=bar&ding=dong#an_anchor")
    }

    "render relative path without parameters" in {
      Uri(path = "/foo/bar").toString must_==("/foo/bar")
    }

    "render relative root path without parameters" in {
      Uri(path = "/").toString must_==("/")
    }

    "render a query string with a single param" in {
      Uri(query = Some("param1=test")).toString must_==("/?param1=test")
    }

    "render a query string with multiple value in a param" in {
      Uri(query = Some("param1=3&param2=2&param2=foo")).toString must_==("/?param1=3&param2=2&param2=foo")
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
      foreach (examples) { e =>
        Uri.fromString(e).get.toString must be_==(e)
      }
    }

  }

  "Uri parameters" should {
    "parse empty query string" in {
      Uri(query = Some("")).multiParams must be_==(Map.empty)
    }
    "parse parameter without key but with empty value" in {
      Uri(query = Some("=")).multiParams must be_==(Map("" -> List("")))
    }
    "parse parameter without key but with value" in {
      Uri(query = Some("=value")).multiParams must be_==(Map("" -> List("value")))
    }
    "parse single parameter with empty value" in {
      Uri(query = Some("param1=")).multiParams must be_==(Map("param1" -> List("")))
    }
    "parse single parameter with value" in {
      Uri(query = Some("param1=value")).multiParams must be_==(Map("param1" -> List("value")))
    }
    "parse single parameter without value" in {
      Uri(query = Some("param1")).multiParams must be_==(Map("param1" -> Nil))
    }
    "parse many parameter with value" in {
      Uri(query = Some("param1=value&param2=value1&param2=value2&param3=value")).multiParams must_==(Map(
          "param1" -> List("value"),
          "param2" -> List("value1", "value2"),
          "param3" -> List("value")))
    }
    "parse many parameter without value" in {
      Uri(query = Some("param1&param2&param3")).multiParams must_==(Map(
          "param1" -> Nil,
          "param2" -> Nil,
          "param3" -> Nil))
    }
  }

  "Uri.params.+" should {
    "add parameter to empty query" in {
      val i = Uri(query = None).params + (("param", Seq("value")))
      i must be_==(Map("param" -> Seq("value")))
    }
    "add parameter" in {
      val i = Uri(query = Some("param1")).params + (("param2", Seq()))
      i must be_==(Map("param1" -> Seq(), "param2" -> Seq()))
    }
    "replace an existing parameter" in {
      val i = Uri(query = Some("param=value")).params + (("param", Seq("value1", "value2")))
      i must be_==(Map("param" -> Seq("value1", "value2")))
    }
    "replace an existing parameter with empty value" in {
      val i = Uri(query = Some("param=value")).params + (("param", Seq()))
      i must be_==(Map("param" -> Seq()))
    }
  }

  "Uri.params.-" should {
    "not do anything on an URI without a query" in {
      val i = Uri(query = None).params - "param"
      i must be_==(Map())
    }
    "not reduce a map if parameter does not match" in {
      val i = Uri(query = Some("param1")).params - "param2"
      i must be_==(Map("param1" -> ""))
    }
    "reduce a map if matching parameter found" in {
      val i = Uri(query = Some("param")).params - "param"
      i must be_==(Map())
    }
  }

  "Uri.params.iterate" should {
    "work on an URI without a query" in {
      foreach (Uri(query = None).params.iterator) { i =>
        throw new Error(s"should not have $i") // should not happen
      }
    }
    "work on empty list" in {
      foreach (Uri(query = Some("")).params.iterator) { i =>
        throw new Error(s"should not have $i") // should not happen
      }
    }
    "work with empty keys" in {
      val u = Uri(query = Some("=value1&=value2&=&"))
      val i = u.params.iterator
      i.next must be_==("" -> "value1")
      i.next must throwA [NoSuchElementException]
    }
    "work on non-empty query string" in {
      val u = Uri(query = Some("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      val i = u.params.iterator
      i.next must be_==("param1" -> "value1")
      i.next must be_==("param2" -> "value4")
      i.next must throwA [NoSuchElementException]
    }
  }

  "Uri.multiParams" should {
    "find first value of parameter with many values" in {
      val u = Uri(query = Some("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      u.multiParams must be_==(
        Map(
          "param1" -> Seq("value1", "value2", "value3"),
          "param2" -> Seq("value4", "value5")))
    }
    "find parameter with empty key and a value" in {
      val u = Uri(query = Some("param1=&=value-of-empty-key&param2=value"))
      u.multiParams must be_==(
        Map(
          "" -> Seq("value-of-empty-key"),
          "param1" -> Seq(""),
          "param2" -> Seq("value")))
    }
    "find first value of parameter with empty key" in {
      Uri(query = Some("=value1&=value2")).multiParams must_== (
          Map("" -> Seq("value1", "value2")))
      Uri(query = Some("&=value1&=value2")).multiParams must_== (
          Map("" -> Seq("value1", "value2")))
      Uri(query = Some("&&&=value1&&&=value2&=&")).multiParams must_== (
          Map("" -> Seq("value1", "value2", "")))
    }
    "find parameter with empty key and without value" in {
      Uri(query = Some("&")).multiParams must_==(Map("" -> Seq()))
      Uri(query = Some("&&")).multiParams must_==(Map("" -> Seq()))
      Uri(query = Some("&&&")).multiParams must_==(Map("" -> Seq()))
    }
    "find parameter with an empty value" in {
      Uri(query = Some("param1=")).multiParams must_==(Map("param1" -> Seq("")))
      Uri(query = Some("param1=&param2=")).multiParams must_== (Map("param1" -> Seq(""), "param2" -> Seq("")))
    }
    "find parameter with single value" in {
      Uri(query = Some("param1=value1&param2=value2")).multiParams must_==(
          Map(
            "param1" -> Seq("value1"),
            "param2" -> Seq("value2")))
    }
    "find parameter without value" in {
      Uri(query = Some("param1&param2&param3")).multiParams must_==(
          Map(
            "param1" -> Seq(),
            "param2" -> Seq(),
            "param3" -> Seq()))
    }
  }

  "Uri.params.get" should {
    "find first value of parameter with many values" in {
      val u = Uri(query = Some("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      u.params.get("param1") must be_==(Some("value1"))
      u.params.get("param2") must be_==(Some("value4"))
    }
    "find parameter with empty key and a value" in {
      val u = Uri(query = Some("param1=&=valueWithEmptyKey&param2=value2"))
      u.params.get("") must be_==(Some("valueWithEmptyKey"))
    }
    "find first value of parameter with empty key" in {
      Uri(query = Some("=value1&=value2")).params.get("") must be_==(Some("value1"))
      Uri(query = Some("&=value1&=value2")).params.get("") must be_==(Some("value1"))
      Uri(query = Some("&&&=value1")).params.get("") must be_==(Some("value1"))
    }
    "find parameter with empty key and without value" in {
      Uri(query = Some("&")).params.get("") must be_==(None)
      Uri(query = Some("&&")).params.get("") must be_==(None)
      Uri(query = Some("&&&")).params.get("") must be_==(None)
    }
    "find parameter with an empty value" in {
      val u = Uri(query = Some("param1=&param2=value2"))
      u.params.get("param1") must be_==(Some(""))
    }
    "find parameter with single value" in {
      val u = Uri(query = Some("param1=value1&param2=value2"))
      u.params.get("param1") must be_==(Some("value1"))
      u.params.get("param2") must be_==(Some("value2"))
    }
    "find parameter without value" in {
      val u = Uri(query = Some("param1&param2&param3"))
      u.params.get("param1") must be_==(None)
      u.params.get("param2") must be_==(None)
      u.params.get("param3") must be_==(None)
    }
    "not find an unknown parameter" in {
      Uri(query = Some("param1&param2&param3")).params.get("param4") must be_==(None)
    }
    "not find anything if query string is empty" in {
      Uri(query = None).params.get("param1") must be_==(None)
    }
  }

  "Uri parameter convenience methods" should {
    "add a parameter if no query is available" in {
      val u = Uri(query = None) +? ("param1", "value")
      u must be_==(Uri(query = Some("param1=value")))
    }
    "add a parameter" in {
      val u = Uri(query = Some("param1=value1&param1=value2")) +? ("param2", "value")
      u must be_==(Uri(query = Some("param1=value1&param1=value2&param2=value")))
    }
    "add a parameter with boolean value" in {
      val u = Uri(query = Some("param1=value1&param1=value2")) +? ("param2", true)
      u must be_==(Uri(query = Some("param1=value1&param1=value2&param2=true")))
    }
    "add a parameter without a value" in {
      val u = Uri(query = Some("param1=value1&param1=value2")) +? ("param2")
      u must be_==(Uri(query = Some("param1=value1&param1=value2&param2")))
    }
    "add a parameter with many values" in {
      val u = Uri() +? ("param1", "value1", "value2")
      u must be_==(Uri(query = Some("param1=value1&param1=value2")))
    }
    "add a parameter with many long values" in {
      val u = Uri() +? ("param1", 1L, -1L)
      u must be_==(Uri(query = Some(s"param1=1&param1=-1")))
    }
    "contains not a parameter" in {
      Uri(query = None) ? "param1" must be_==(false)
    }
    "contains an empty parameter" in {
      Uri(query = Some("")) ? "" must be_==(true)
      Uri(query = Some("")) ? "param" must be_==(false)
      Uri(query = Some("&&=value&&")) ? "" must be_==(true)
      Uri(query = Some("&&=value&&")) ? "param" must be_==(false)
    }
    "contains a parameter" in {
      Uri(query = Some("param1=value&param1=value")) ? "param1" must be_==(true)
      Uri(query = Some("param1=value&param2=value")) ? "param2" must be_==(true)
      Uri(query = Some("param1=value&param2=value")) ? "param3" must be_==(false)
    }
    "contains a parameter with many values" in {
      Uri(query = Some("param1=value1&param1=value2&param1=value3")) ? "param1" must be_==(true)
    }
    "contains a parameter without a value" in {
      Uri(query = Some("param1")) ? "param1" must be_==(true)
    }
    "contains with many parameters" in {
      Uri(query = Some("param1=value1&param1=value2&param2&=value3")) ? "param1" must be_==(true)
      Uri(query = Some("param1=value1&param1=value2&param2&=value3")) ? "param2" must be_==(true)
      Uri(query = Some("param1=value1&param1=value2&param2&=value3")) ? "" must be_==(true)
      Uri(query = Some("param1=value1&param1=value2&param2&=value3")) ? "param3" must be_==(false)
    }
    "remove a parameter if present" in {
      val u = Uri(query = Some("param1=value&param2=value")) -? ("param1")
      u must be_==(Uri(query = Some("param2=value")))
    }
    "remove an empty parameter from an empty query string" in {
      val u = Uri(query = Some("")) -? ("")
      u must be_==(Uri(query = None))
    }
    "remove nothing if parameter is not present" in {
      val u = Uri(query = Some("param1=value&param2=value"))
      u -? ("param3") must be_==(u)
    }
    "remove the last parameter" in {
      val u = Uri(query = Some("param1=value")) -? ("param1")
      u must be_==(Uri())
    }
    "replace a parameter" in {
      val u = Uri(query = Some("param1=value&param2=value")) +? ("param1", "newValue")
      u must be_==(Uri(query = Some("param1=newValue&param2=value")))
    }
    "replace a parameter without a value" in {
      val u = Uri(query = Some("param1=value1&param1=value2&param2=value")) +? ("param2")
      u must be_==(Uri(query = Some("param1=value1&param1=value2&param2")))
    }
    "replace the same parameter" in {
      val u = Uri(query = Some("param1=value1&param1=value2&param2")) +? ("param1", "value1", "value2")
      u must be_==(Uri(query = Some("param1=value1&param1=value2&param2")))
    }
    "replace the same parameter without a value" in {
      val u = Uri(query = Some("param1=value1&param1=value2&param2")) +? ("param2")
      u must be_==(Uri(query = Some("param1=value1&param1=value2&param2")))
    }
    "replace a parameter set" in {
      val u = Uri(query = Some("param1=value1&param1=value2")) +? ("param1", "value")
      u must be_==(Uri(query = Some("param1=value")))
    }
    "set a parameter with a value" in {
      val ps = Map("param" -> List("value"))
      Uri() =? ps must be_==(Uri(query = Some("param=value")))
    }
    "set a parameter with a boolean values" in {
      val ps = Map("param" -> List(true, false))
      Uri() =? ps must be_==(Uri(query = Some("param=true&param=false")))
    }
    "set a parameter with a char values" in {
      val ps = Map("param" -> List('x', 'y'))
      Uri() =? ps must be_==(Uri(query = Some("param=x&param=y")))
    }
    "set a parameter with a double values" in {
      val ps = Map("param" -> List(1.2, 2.1))
      Uri() =? ps must be_==(Uri(query = Some("param=1.2&param=2.1")))
    }
    "set a parameter with a float values" in {
      val ps = Map("param" -> List(1.2F, 2.1F))
      Uri() =? ps must be_==(Uri(query = Some("param=1.2&param=2.1")))
    }
    "set a parameter with a integer values" in {
      val ps = Map("param" -> List(1, 2, 3))
      Uri() =? ps must be_==(Uri(query = Some("param=1&param=2&param=3")))
    }
    "set a parameter with a long values" in {
      val ps = Map("param" -> List(Long.MaxValue, 0L, Long.MinValue))
      Uri() =? ps must be_==(Uri(query = Some("param=9223372036854775807&param=0&param=-9223372036854775808")))
    }
    "set a parameter with a short values" in {
      val ps = Map("param" -> List(Short.MaxValue, Short.MinValue))
      Uri() =? ps must be_==(Uri(query = Some("param=32767&param=-32768")))
    }
    "set a parameter with a string values" in {
      val ps = Map("param" -> List("some", "none"))
      Uri() =? ps must be_==(Uri(query = Some("param=some&param=none")))
    }
    "set a parameter without a value" in {
      val ps: Map[String, List[String]] = Map("param" -> Nil)
      Uri() =? ps must be_==(Uri(query = Some("param")))
    }
    "set many parameters" in {
      val ps = Map("param1" -> Nil, "param2" -> List("value1", "value2"), "param3" -> List("value"))
      Uri() =? ps must be_==(Uri(query = Some("param1&param2=value1&param2=value2&param3=value")))
    }
    "set the same parameters again" in {
      val ps = Map("param" -> List("value"))
      val u = Uri(query = Some("param=value"))
      u =? ps must be_==(u =? ps)
    }
  }

}
