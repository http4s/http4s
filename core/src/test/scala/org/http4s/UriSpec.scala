package org.http4s

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Uri.Authority
import org.http4s.parser.RequestUriParser
import scala.util.Success

/**
 * Created by brycea on 3/18/14.
 */
class UriSpec extends WordSpec with Matchers {

  "Uri" should {

    /* case class Uri (
        scheme: Option[CaseInsensitiveString] = None,
        authority: Option[Authority] = None,
        path: Path = "/",
        query: Option[Query] = None,
        fragment: Option[Fragment] = None
      )
      */



    // RFC 3986 examples
    // http://tools.ietf.org/html/rfc3986#section-1.1.2

    // http://www.ietf.org/rfc/rfc2396.txt

    val absoluteUris : Seq[(String, Uri)] = Seq(
      ("http://www.google.com", Uri(Some("http".ci), Some(Authority(host = "www.google.com".ci)))),
      ("http://www.google.com/foo?bar=baz",
        Uri(Some("http".ci), Some(Authority(host = "www.google.com".ci)), "/foo", Some("bar=baz"))),
      ("http://192.168.1.1",
        Uri(Some("http".ci), Some(Authority(host = "192.168.1.1".ci)))) ,
      ("http://192.168.1.1:80/c?GB=object&Class=one",
        Uri(Some("http".ci), Some(Authority(host = "192.168.1.1".ci, port = Some(80))), "/c", Some("GB=object&Class=one"))) //,
//      ("http://[2001:db8::7]/c?GB=object&Class=one",
//        Uri(Some("http".ci), Some(Authority(host = "2001:db8::7".ci)), "/c", Some("GB=object&Class=one"))),
//      ("mailto:John.Doe@example.com",
//        Uri(Some("mailto".ci), Some(Authority(userInfo = Some("John.Doe"), host = "example.com".ci))))
    )

    val relativeUris: Seq[(String, Uri)] = Seq(
      ("/foo/bar", Uri(path = "/foo/bar")),
      ("/foo/bar?foo=bar&ding=dong", Uri(path="/foo/bar", query = Some("foo=bar&ding=dong")))
    )

    "Parse a ip6 address" in {
      new RequestUriParser("2001:db8::7", CharacterSet.`UTF-8`.charset).IpV6Address.run() should equal(Success())
    }

    "Parse absolute URIs" in {
      absoluteUris.foreach { case (str, uri) =>
        Uri.fromString(str) should equal(uri)
      }
    }

    "Parse relative URIs" in {
      relativeUris.foreach { case (str, uri) =>
        Uri.fromString(str) should equal(uri)
      }
    }


  }

}
