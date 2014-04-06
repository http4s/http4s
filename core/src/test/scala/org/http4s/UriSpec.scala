package org.http4s

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Uri.Authority
import org.http4s.parser.RequestUriParser
import scala.util.Success
import org.http4s.util.string._

/**
 * Created by brycea on 3/18/14.
 */
class UriSpec extends WordSpec with Matchers {

  "Uri" should {

    // RFC 3986 examples
    // http://tools.ietf.org/html/rfc3986#section-1.1.2

    // http://www.ietf.org/rfc/rfc2396.txt

    "Parse a ip6 address" in {

      val v = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield (f + "::" + b))

      v.foreach(new RequestUriParser(_, CharacterSet.`UTF-8`.charset)
                    .IpV6Address.run() should equal(Success()))

    }

    "handle port configurations" in {
      val portExamples: Seq[(String, Uri)] = Seq(
        ("http://foo.com", Uri(Some("http".ci), Some(Authority(host = "foo.com".ci, port = None)))),
        ("http://foo.com:", Uri(Some("http".ci), Some(Authority(host = "foo.com".ci, port = None)))),
        ("http://foo.com:80", Uri(Some("http".ci), Some(Authority(host = "foo.com".ci, port = Some(80)))))
      )

      check(portExamples)
    }

    "Parse absolute URIs" in {
      val absoluteUris : Seq[(String, Uri)] = Seq(
        ("http://www.foo.com", Uri(Some("http".ci), Some(Authority(host = "www.foo.com".ci)))),
        ("http://www.foo.com/foo?bar=baz",
          Uri(Some("http".ci), Some(Authority(host = "www.foo.com".ci)), "/foo", Some("bar=baz"))),
        ("http://192.168.1.1",
          Uri(Some("http".ci), Some(Authority(host = "192.168.1.1".ci)))) ,
        ("http://192.168.1.1:80/c?GB=object&Class=one",
          Uri(Some("http".ci), Some(Authority(host = "192.168.1.1".ci, port = Some(80))), "/c", Some("GB=object&Class=one"))),
        ("http://[2001:db8::7]/c?GB=object&Class=one",
          Uri(Some("http".ci), Some(Authority(host = "2001:db8::7".ci)), "/c", Some("GB=object&Class=one"))),
        ("mailto:John.Doe@example.com",
          Uri(Some("mailto".ci), path = "John.Doe@example.com"))
      )

      check(absoluteUris)
    }

    "Parse relative URIs" in {
      val relativeUris: Seq[(String, Uri)] = Seq(
        ("/foo/bar", Uri(path = "/foo/bar")),
        ("/foo/bar?foo=bar&ding=dong", Uri(path="/foo/bar", query = Some("foo=bar&ding=dong"))),
        ("/", Uri())
      )

      check(relativeUris)
    }

    def check(items: Seq[(String, Uri)]) = items.foreach { case (str, uri) =>
      Uri.fromString(str).get should equal(uri)
    }

  }

}
