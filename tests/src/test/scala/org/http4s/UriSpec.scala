package org.http4s

import cats.implicits.{catsSyntaxEither => _, _}
import cats.kernel.laws.discipline.EqTests
import java.nio.file.Paths
import org.http4s.Uri._
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.specs2.matcher.MustThrownMatchers

// TODO: this needs some more filling out
class UriSpec extends Http4sSpec with MustThrownMatchers {

  sealed case class Ttl(seconds: Int)
  object Ttl {
    implicit val queryParamInstance = new QueryParamEncoder[Ttl] with QueryParam[Ttl] {
      def key: QueryParameterKey = QueryParameterKey("ttl")
      def encode(value: Ttl): QueryParameterValue = QueryParameterValue(value.seconds.toString)
    }
  }

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  "Uri" should {
    "fromString" in {
      "Not UrlDecode the query String" in {
        getUri("http://localhost:8080/blah?x=abc&y=ijk").query must_=== Query.fromPairs(
          "x" -> "abc",
          "y" -> "ijk")
      }

      "Not UrlDecode the uri fragment" in {
        getUri("http://localhost:8080/blah#x=abc&y=ijk").fragment must_=== Some("x=abc&y=ijk")
      }

      "parse scheme correctly" in {
        val uri = getUri("http://localhost/")
        uri.scheme must_=== Some(Scheme.http)
      }

      "parse the authority correctly" in {
        "when uri has trailing slash" in {
          val uri = getUri("http://localhost/")
          uri.authority.get.host must_=== RegName("localhost")
        }

        "when uri does not have trailing slash" in {
          val uri = getUri("http://localhost")
          uri.authority.get.host must_=== RegName("localhost")
        }

        "if there is none" in {
          val uri = getUri("/foo/bar")
          uri.authority must_=== None
        }
      }

      "parse port correctly" >> {
        "if there is one" in {
          val uri = getUri("http://localhost:8080/")
          uri.port must_=== Some(8080)
        }
        "if there is none" in {
          val uri = getUri("http://localhost/")
          uri.port must_=== None
        }
      }

      "both authority and port" in {
        val auth = getUri("http://localhost:8080/").authority.get
        auth.host must_=== RegName("localhost")
        auth.port must_=== Some(8080)
      }

      "provide a useful error message if string argument is not url-encoded" in {
        Uri.fromString("http://example.org/a file") must beLeft(
          ParseFailure(
            "Invalid URI",
            """Invalid input ' ', expected Pchar, '/', '?', '#' or 'EOI' (line 1, column 21):
http://example.org/a file
                    ^""".replace("\r", "")
          ))
      }
    }

    "support a '/' operator when original uri has trailing slash" in {
      val uri = getUri("http://localhost:8080/")
      val newUri = uri / "echo"
      newUri must_== getUri("http://localhost:8080/echo")
    }

    "support a '/' operator when original uri has no trailing slash" in {
      val uri = getUri("http://localhost:8080")
      val newUri = uri / "echo"
      newUri must_== getUri("http://localhost:8080/echo")
    }
  }

  "Uri's with a query and fragment" should {
    "parse properly" in {
      val uri = getUri("http://localhost:8080/blah?x=abc#y=ijk")
      uri.query must_== Query.fromPairs("x" -> "abc")
      uri.fragment must_== Some("y=ijk")
    }
  }

  "Uri Query decoding" should {

    def getQueryParams(uri: String): Map[String, String] = getUri(uri).params

    "Handle queries with no spaces properly" in {
      getQueryParams("http://localhost:8080/blah?x=abc&y=ijk") must_== Map(
        "x" -> "abc",
        "y" -> "ijk")
      getQueryParams("http://localhost:8080/blah?") must_== Map("" -> "")
      getQueryParams("http://localhost:8080/blah") must_== Map.empty
    }

    "Handle queries with spaces properly" in {
      // Issue #75
      getQueryParams("http://localhost:8080/blah?x=a+bc&y=ijk") must_== Map(
        "x" -> "a bc",
        "y" -> "ijk")
      getQueryParams("http://localhost:8080/blah?x=a%20bc&y=ijk") must_== Map(
        "x" -> "a bc",
        "y" -> "ijk")
    }

  }

  "Uri copy" should {
    "support updating the schema" in {
      uri("http://example.com/").copy(scheme = Scheme.https.some) must_== uri(
        "https://example.com/")
      // Must add the authority to set the scheme and host
      uri("/route/").copy(
        scheme = Scheme.https.some,
        authority = Some(Authority(None, RegName("example.com")))) must_== uri(
        "https://example.com/route/")
      // You can add a port too
      uri("/route/").copy(
        scheme = Scheme.https.some,
        authority = Some(Authority(None, RegName("example.com"), Some(8443)))) must_== uri(
        "https://example.com:8443/route/")
    }
  }

  "Uri toString" should {
    "render default URI" in {
      Uri().toString must be_==("")
    }

    "render a IPv6 address, should be wrapped in brackets" in {
      val variants = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield (f + "::" + b))

      foreach(variants) { s =>
        Uri(
          Some(Scheme.http),
          Some(Authority(host = IPv6(s.ci))),
          "/foo",
          Query.fromPairs("bar" -> "baz")).toString must_==
          (s"http://[$s]/foo?bar=baz")
      }
    }

    "render URL with parameters" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(host = RegName("www.foo.com".ci))),
        "/foo",
        Query.fromPairs("bar" -> "baz")).toString must_== ("http://www.foo.com/foo?bar=baz")
    }

    "render URL with port" in {
      Uri(Some(Scheme.http), Some(Authority(host = RegName("www.foo.com".ci), port = Some(80)))).toString must_== ("http://www.foo.com:80")
    }

    "render URL without port" in {
      Uri(Some(Scheme.http), Some(Authority(host = RegName("www.foo.com".ci)))).toString must_== ("http://www.foo.com")
    }

    "render IPv4 URL with parameters" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(80))),
        "/c",
        Query.fromPairs("GB" -> "object", "Class" -> "one")).toString must_== ("http://192.168.1.1:80/c?GB=object&Class=one")
    }

    "render IPv4 URL with port" in {
      Uri(Some(Scheme.http), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(8080)))).toString must_== ("http://192.168.1.1:8080")
    }

    "render IPv4 URL without port" in {
      Uri(Some(Scheme.http), Some(Authority(host = IPv4("192.168.1.1".ci)))).toString must_== ("http://192.168.1.1")
    }

    "render IPv6 URL with parameters" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(host = IPv6("2001:db8::7".ci))),
        "/c",
        Query.fromPairs("GB" -> "object", "Class" -> "one")).toString must_== ("http://[2001:db8::7]/c?GB=object&Class=one")
    }

    "render IPv6 URL with port" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(
          host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci),
          port = Some(8080)))).toString must_== ("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]:8080")
    }

    "render IPv6 URL without port" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci)))).toString must_== ("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]")
    }

    "not append a '/' unless it's in the path" in {
      uri("http://www.example.com").toString must_== ("http://www.example.com")
    }

    "render email address" in {
      Uri(Some(scheme"mailto"), path = "John.Doe@example.com").toString must_== ("mailto:John.Doe@example.com")
    }

    "render an URL with username and password" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(Some("username:password"), RegName("some.example.com"), None)),
        "/",
        Query.empty,
        None).toString must_== ("http://username:password@some.example.com/")
    }

    "render an URL with username and password, path and params" in {
      Uri(
        Some(Scheme.http),
        Some(Authority(Some("username:password"), RegName("some.example.com"), None)),
        "/some/path",
        Query.fromString("param1=5&param-without-value"),
        None
      ).toString must_== ("http://username:password@some.example.com/some/path?param1=5&param-without-value")
    }

    "render relative URI with empty query string" in {
      Uri(path = "/", query = Query.fromString(""), fragment = None).toString must_== ("/?")
    }

    "render relative URI with empty query string and fragment" in {
      Uri(path = "/", query = Query.fromString(""), fragment = Some("")).toString must_== ("/?#")
    }

    "render relative URI with empty fragment" in {
      Uri(path = "/", query = Query.empty, fragment = Some("")).toString must_== ("/#")
    }

    "render relative path with fragment" in {
      Uri(path = "/foo/bar", fragment = Some("an-anchor")).toString must_== ("/foo/bar#an-anchor")
    }

    "render relative path with parameters" in {
      Uri(path = "/foo/bar", query = Query.fromString("foo=bar&ding=dong")).toString must_== ("/foo/bar?foo=bar&ding=dong")
    }

    "render relative path with parameters and fragment" in {
      Uri(
        path = "/foo/bar",
        query = Query.fromString("foo=bar&ding=dong"),
        fragment = Some("an_anchor")).toString must_== ("/foo/bar?foo=bar&ding=dong#an_anchor")
    }

    "render relative path without parameters" in {
      Uri(path = "/foo/bar").toString must_== ("/foo/bar")
    }

    "render relative root path without parameters" in {
      Uri(path = "/").toString must_== ("/")
    }

    "render a query string with a single param" in {
      Uri(query = Query.fromString("param1=test")).toString must_== ("?param1=test")
    }

    "render a query string with multiple value in a param" in {
      Uri(query = Query.fromString("param1=3&param2=2&param2=foo")).toString must_== ("?param1=3&param2=2&param2=foo")
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
        "/relative/URI/with/absolute/path/to/resource.txt"
      )
      foreach(examples) { e =>
        Uri.fromString(e).map(_.toString) must beRight(e)
      }
    }

    "handle brackets in query string" in {
      // These are illegal, but common in the wild.  We will be "conservative
      // in our sending behavior and liberal in our receiving behavior", and
      // encode them.
      Uri
        .fromString("http://localhost:8080/index?filter[state]=public")
        .map(_.toString) must beRight("http://localhost:8080/index?filter%5Bstate%5D=public")
    }

    "round trip with toString" in forAll { uri: Uri =>
      Uri.fromString(uri.toString) must be(uri.toString)
    }.pendingUntilFixed
  }

  "Uri parameters" should {
    "parse empty query string" in {
      Uri(query = Query.fromString("")).multiParams must be_==(Map("" -> Nil))
    }
    "parse parameter without key but with empty value" in {
      Uri(query = Query.fromString("=")).multiParams must be_==(Map("" -> List("")))
    }
    "parse parameter without key but with value" in {
      Uri(query = Query.fromString("=value")).multiParams must be_==(Map("" -> List("value")))
    }
    "parse single parameter with empty value" in {
      Uri(query = Query.fromString("param1=")).multiParams must be_==(Map("param1" -> List("")))
    }
    "parse single parameter with value" in {
      Uri(query = Query.fromString("param1=value")).multiParams must be_==(
        Map("param1" -> List("value")))
    }
    "parse single parameter without value" in {
      Uri(query = Query.fromString("param1")).multiParams must be_==(Map("param1" -> Nil))
    }
    "parse many parameter with value" in {
      Uri(query = Query.fromString("param1=value&param2=value1&param2=value2&param3=value")).multiParams must_== (Map(
        "param1" -> List("value"),
        "param2" -> List("value1", "value2"),
        "param3" -> List("value")))
    }
    "parse many parameter without value" in {
      Uri(query = Query.fromString("param1&param2&param3")).multiParams must_== (Map(
        "param1" -> Nil,
        "param2" -> Nil,
        "param3" -> Nil))
    }
  }

  "Uri.params.+" should {
    "add parameter to empty query" in {
      val i = Uri(query = Query.empty).params + (("param", Seq("value")))
      i must be_==(Map("param" -> Seq("value")))
    }
    "add parameter" in {
      val i = Uri(query = Query.fromString("param1")).params + (("param2", Seq()))
      i must be_==(Map("param1" -> Seq(), "param2" -> Seq()))
    }
    "replace an existing parameter" in {
      val i = Uri(query = Query.fromString("param=value")).params + (
        (
          "param",
          Seq("value1", "value2")))
      i must be_==(Map("param" -> Seq("value1", "value2")))
    }
    "replace an existing parameter with empty value" in {
      val i = Uri(query = Query.fromString("param=value")).params + (("param", Seq()))
      i must be_==(Map("param" -> Seq()))
    }
  }

  "Uri.params.-" should {
    "not do anything on an URI without a query" in {
      val i = Uri(query = Query.empty).params - "param"
      i must be_==(Map())
    }
    "not reduce a map if parameter does not match" in {
      val i = Uri(query = Query.fromString("param1")).params - "param2"
      i must be_==(Map("param1" -> ""))
    }
    "reduce a map if matching parameter found" in {
      val i = Uri(query = Query.fromString("param")).params - "param"
      i must be_==(Map())
    }
  }

  "Uri.params.iterate" should {
    "work on an URI without a query" in {
      foreach(Uri(query = Query.empty).params.iterator) { i =>
        ko(s"should not have $i") // should not happen
      }
    }
    "work on empty list" in {
      foreach(Uri(query = Query.fromString("")).params.iterator) {
        case (k, v) =>
          k must_== ""
          v must_== ""
      }
    }
    "work with empty keys" in {
      val u = Uri(query = Query.fromString("=value1&=value2&=&"))
      val i = u.params.iterator
      i.next must be_==("" -> "value1")
      i.next must throwA[NoSuchElementException]
    }
    "work on non-empty query string" in {
      val u = Uri(
        query =
          Query.fromString("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      val i = u.params.iterator
      i.next must be_==("param1" -> "value1")
      i.next must be_==("param2" -> "value4")
      i.next must throwA[NoSuchElementException]
    }
  }

  "Uri.multiParams" should {
    "find first value of parameter with many values" in {
      val u = Uri(
        query =
          Query.fromString("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      u.multiParams must be_==(
        Map("param1" -> Seq("value1", "value2", "value3"), "param2" -> Seq("value4", "value5")))
    }
    "find parameter with empty key and a value" in {
      val u = Uri(query = Query.fromString("param1=&=value-of-empty-key&param2=value"))
      u.multiParams must be_==(
        Map("" -> Seq("value-of-empty-key"), "param1" -> Seq(""), "param2" -> Seq("value")))
    }
    "find first value of parameter with empty key" in {
      Uri(query = Query.fromString("=value1&=value2")).multiParams must_== (Map(
        "" -> Seq("value1", "value2")))
      Uri(query = Query.fromString("&=value1&=value2")).multiParams must_== (Map(
        "" -> Seq("value1", "value2")))
      Uri(query = Query.fromString("&&&=value1&&&=value2&=&")).multiParams must_== (Map(
        "" -> Seq("value1", "value2", "")))
    }
    "find parameter with empty key and without value" in {
      Uri(query = Query.fromString("&")).multiParams must_== (Map("" -> Seq()))
      Uri(query = Query.fromString("&&")).multiParams must_== (Map("" -> Seq()))
      Uri(query = Query.fromString("&&&")).multiParams must_== (Map("" -> Seq()))
    }
    "find parameter with an empty value" in {
      Uri(query = Query.fromString("param1=")).multiParams must_== (Map("param1" -> Seq("")))
      Uri(query = Query.fromString("param1=&param2=")).multiParams must_== (Map(
        "param1" -> Seq(""),
        "param2" -> Seq("")))
    }
    "find parameter with single value" in {
      Uri(query = Query.fromString("param1=value1&param2=value2")).multiParams must_== (Map(
        "param1" -> Seq("value1"),
        "param2" -> Seq("value2")))
    }
    "find parameter without value" in {
      Uri(query = Query.fromString("param1&param2&param3")).multiParams must_== (Map(
        "param1" -> Seq(),
        "param2" -> Seq(),
        "param3" -> Seq()))
    }
  }

  "Uri.params.get" should {
    "find first value of parameter with many values" in {
      val u = Uri(
        query =
          Query.fromString("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      u.params.get("param1") must be_==(Some("value1"))
      u.params.get("param2") must be_==(Some("value4"))
    }
    "find parameter with empty key and a value" in {
      val u = Uri(query = Query.fromString("param1=&=valueWithEmptyKey&param2=value2"))
      u.params.get("") must be_==(Some("valueWithEmptyKey"))
    }
    "find first value of parameter with empty key" in {
      Uri(query = Query.fromString("=value1&=value2")).params.get("") must be_==(Some("value1"))
      Uri(query = Query.fromString("&=value1&=value2")).params.get("") must be_==(Some("value1"))
      Uri(query = Query.fromString("&&&=value1")).params.get("") must be_==(Some("value1"))
    }
    "find parameter with empty key and without value" in {
      Uri(query = Query.fromString("&")).params.get("") must be_==(None)
      Uri(query = Query.fromString("&&")).params.get("") must be_==(None)
      Uri(query = Query.fromString("&&&")).params.get("") must be_==(None)
    }
    "find parameter with an empty value" in {
      val u = Uri(query = Query.fromString("param1=&param2=value2"))
      u.params.get("param1") must be_==(Some(""))
    }
    "find parameter with single value" in {
      val u = Uri(query = Query.fromString("param1=value1&param2=value2"))
      u.params.get("param1") must be_==(Some("value1"))
      u.params.get("param2") must be_==(Some("value2"))
    }
    "find parameter without value" in {
      val u = Uri(query = Query.fromString("param1&param2&param3"))
      u.params.get("param1") must be_==(None)
      u.params.get("param2") must be_==(None)
      u.params.get("param3") must be_==(None)
    }
    "not find an unknown parameter" in {
      Uri(query = Query.fromString("param1&param2&param3")).params.get("param4") must be_==(None)
    }
    "not find anything if query string is empty" in {
      Uri(query = Query.empty).params.get("param1") must be_==(None)
    }
  }

  "Uri parameter convenience methods" should {
    "add a parameter if no query is available" in {
      val u = Uri(query = Query.empty) +? ("param1", "value")
      u must be_==(Uri(query = Query.fromString("param1=value")))
    }
    "add a parameter" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param2", "value")
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2=value")))
    }
    "add a parameter with boolean value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param2", true)
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2=true")))
    }
    "add a parameter without a value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param2")
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2")))
    }
    "add a parameter with many values" in {
      val u = Uri() +? ("param1", Seq("value1", "value2"))
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2")))
    }
    "add a parameter with many long values" in {
      val u = Uri() +? ("param1", Seq(1L, -1L))
      u must be_==(Uri(query = Query.fromString(s"param1=1&param1=-1")))
    }
    "add a query parameter with a QueryParamEncoder" in {
      val u = Uri() +? ("test", Ttl(2))
      u must be_==(Uri(query = Query.fromString(s"test=2")))
    }
    "add a query parameter with a QueryParamEncoder and an implicit key" in {
      val u = Uri() +*? (Ttl(2))
      u must be_==(Uri(query = Query.fromString(s"ttl=2")))
    }
    "Work with queryParam" in {
      val u = Uri().withQueryParam[Ttl]
      u must be_==(Uri(query = Query.fromString(s"ttl")))
    }
    "add an optional query parameter (Just)" in {
      val u = Uri() +?? ("param1", Some(2))
      u must be_==(Uri(query = Query.fromString(s"param1=2")))
    }
    "add an optional query parameter (Empty)" in {
      val u = Uri() +?? ("param1", None: Option[Int])
      u must be_==(Uri(query = Query.empty))
    }
    "contains not a parameter" in {
      Uri(query = Query.empty) ? "param1" must be_==(false)
    }
    "contains an empty parameter" in {
      Uri(query = Query.fromString("")) ? "" must be_==(true)
      Uri(query = Query.fromString("")) ? "param" must be_==(false)
      Uri(query = Query.fromString("&&=value&&")) ? "" must be_==(true)
      Uri(query = Query.fromString("&&=value&&")) ? "param" must be_==(false)
    }
    "contains a parameter" in {
      Uri(query = Query.fromString("param1=value&param1=value")) ? "param1" must be_==(true)
      Uri(query = Query.fromString("param1=value&param2=value")) ? "param2" must be_==(true)
      Uri(query = Query.fromString("param1=value&param2=value")) ? "param3" must be_==(false)
    }
    "contains a parameter with many values" in {
      Uri(query = Query.fromString("param1=value1&param1=value2&param1=value3")) ? "param1" must be_==(
        true)
    }
    "contains a parameter without a value" in {
      Uri(query = Query.fromString("param1")) ? "param1" must be_==(true)
    }
    "contains with many parameters" in {
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "param1" must be_==(
        true)
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "param2" must be_==(
        true)
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "" must be_==(
        true)
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "param3" must be_==(
        false)
    }
    "remove a parameter if present" in {
      val u = Uri(query = Query.fromString("param1=value&param2=value")) -? ("param1")
      u must be_==(Uri(query = Query.fromString("param2=value")))
    }
    "remove an empty parameter from an empty query string" in {
      val u = Uri(query = Query.fromString("")) -? ("")
      u must be_==(Uri(query = Query.empty))
    }
    "remove nothing if parameter is not present" in {
      val u = Uri(query = Query.fromString("param1=value&param2=value"))
      u -? ("param3") must be_==(u)
    }
    "remove the last parameter" in {
      val u = Uri(query = Query.fromString("param1=value")) -? ("param1")
      u must be_==(Uri())
    }
    "replace a parameter" in {
      val u = Uri(query = Query.fromString("param1=value&param2=value")) +? ("param1", "newValue")
      u.multiParams must be_==(
        Uri(query = Query.fromString("param1=newValue&param2=value")).multiParams)
    }
    "replace a parameter without a value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param2=value")) +? ("param2")
      u.multiParams must be_==(
        Uri(query = Query.fromString("param1=value1&param1=value2&param2")).multiParams)
    }
    "replace the same parameter" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param2")) +? ("param1", Seq(
        "value1",
        "value2"))
      u.multiParams must be_==(
        Uri(query = Query.fromString("param1=value1&param1=value2&param2")).multiParams)
    }
    "replace the same parameter without a value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param2")) +? ("param2")
      u.multiParams must be_==(
        Uri(query = Query.fromString("param1=value1&param1=value2&param2")).multiParams)
    }
    "replace a parameter set" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param1", "value")
      u.multiParams must be_==(Uri(query = Query.fromString("param1=value")).multiParams)
    }
    "set a parameter with a value" in {
      val ps = Map("param" -> List("value"))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=value")))
    }
    "set a parameter with a boolean values" in {
      val ps = Map("param" -> List(true, false))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=true&param=false")))
    }
    "set a parameter with a double values" in {
      val ps = Map("param" -> List(1.2, 2.1))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=1.2&param=2.1")))
    }
    "set a parameter with a float values" in {
      val ps = Map("param" -> List(1.2F, 2.1F))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=1.2&param=2.1")))
    }
    "set a parameter with a integer values" in {
      val ps = Map("param" -> List(1, 2, 3))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=1&param=2&param=3")))
    }
    "set a parameter with a long values" in {
      val ps = Map("param" -> List(Long.MaxValue, 0L, Long.MinValue))
      Uri() =? ps must be_==(
        Uri(
          query = Query.fromString("param=9223372036854775807&param=0&param=-9223372036854775808")))
    }
    "set a parameter with a short values" in {
      val ps = Map("param" -> List(Short.MaxValue, Short.MinValue))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=32767&param=-32768")))
    }
    "set a parameter with a string values" in {
      val ps = Map("param" -> List("some", "none"))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=some&param=none")))
    }
    "set a parameter without a value" in {
      val ps: Map[String, List[String]] = Map("param" -> Nil)
      Uri() =? ps must be_==(Uri(query = Query.fromString("param")))
    }
    "set many parameters" in {
      val ps = Map("param1" -> Nil, "param2" -> List("value1", "value2"), "param3" -> List("value"))
      Uri() =? ps must be_==(
        Uri(query = Query.fromString("param1&param2=value1&param2=value2&param3=value")))
    }
    "set the same parameters again" in {
      val ps = Map("param" -> List("value"))
      val u = Uri(query = Query.fromString("param=value"))
      u =? ps must be_==(u =? ps)
    }
  }

  "Uri.withFragment convenience method" should {
    "set a Fragment" in {
      val u = Uri(path = "/")
      val updated = u.withFragment("nonsense")
      updated.renderString must_== "/#nonsense"
    }
    "set a new Fragment" in {
      val u = Uri(path = "/", fragment = Some("adjakda"))
      val updated = u.withFragment("nonsense")
      updated.renderString must_== "/#nonsense"
    }
    "set no Fragment on a null String" in {
      val u = Uri(path = "/", fragment = Some("adjakda"))
      val evilString: String = null
      val updated = u.withFragment(evilString)
      updated.renderString must_== "/"
    }
  }

  "Uri.withoutFragment convenience method" should {
    "unset a Fragment" in {
      val u = Uri(path = "/", fragment = Some("nonsense"))
      val updated = u.withoutFragment
      updated.renderString must_== "/"
    }

  }

  "Uri.renderString" should {
    "Encode special chars in the query" in {
      val u = Uri(path = "/").withQueryParam("foo", " !$&'()*+,;=:/?@~")
      u.renderString must_== "/?foo=%20%21%24%26%27%28%29%2A%2B%2C%3B%3D%3A/?%40~"
    }
    "Encode special chars in the fragment" in {
      val u = Uri(path = "/", fragment = Some(" !$&'()*+,;=:/?@~"))
      u.renderString must_== "/#%20!$&'()*+,;=:/?@~"
    }
  }

  "Uri relative resolution" should {

    val base = getUri("http://a/b/c/d;p?q")

    "correctly remove ./.. sequences" >> {
      implicit class checkDotSequences(path: String) {
        def removingDotsShould_==(expected: String) =
          s"$path -> $expected" in { removeDotSegments(path) must_== expected }
      }

      // from RFC 3986 sec 5.2.4
      "mid/content=5/../6".removingDotsShould_==("mid/6")
      "/a/b/c/./../../g".removingDotsShould_==("/a/g")
    }

    implicit class check(relative: String) {
      def shouldResolveTo(expected: String) =
        s"$base @ $relative -> $expected" in {
          base.resolve(getUri(relative)) must_== getUri(expected)
        }
    }

    "correctly resolve RFC 3986 sec 5.4 normal examples" >> {
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

    "correctly resolve RFC 3986 sec 5.4 abnormal examples" >> {
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

    "correctly remove dot segments in other examples" >> prop { input: String =>
      val prefix = "/this/isa/prefix/"
      val processed = Uri.removeDotSegments(input)
      val path = Paths.get(prefix, processed).normalize
      path.startsWith(Paths.get(prefix)) must beTrue
      processed must not contain "./"
      processed must not contain "../"
    }.setGen(pathGen)
  }

  "Uri.equals" should {
    "be false between an empty path and a trailing slash after an authority" in {
      uri("http://example.com") must_!= uri("http://example.com/")
    }
  }

  "Eq instance for Uri" should {
    "be lawful" in {
      checkAll("Eq[Uri]", EqTests[Uri].eqv)
    }
  }

  "/" should {
    "encode space as %20" in {
      uri("http://example.com/") / " " must_== uri("http://example.com/%20")
    }

    "encode generic delimiters that aren't pchars" in {
      // ":" and "@" are valid pchars
      uri("http://example.com") / ":/?#[]@" must_== uri("http://example.com/:%2F%3F%23%5B%5D@")
    }

    "encode percent sequences" in {
      uri("http://example.com") / "%2F" must_== uri("http://example.com/%252F")
    }

    "not encode sub-delims" in {
      uri("http://example.com") / "!$&'()*+,;=" must_== uri("http://example.com/!$&'()*+,;=")
    }

    "UTF-8 encode characters" in {
      uri("http://example.com/") / "ö" must_== uri("http://example.com/%C3%B6")
    }

    "not make bad URIs" >> forAll { s: String =>
      Uri.fromString((uri("http://example.com/") / s).toString) must beRight
    }
  }
}
