package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Header.{`Set-Cookie`}
import scalaz.Validation
import org.http4s.{Header, Cookie}

class SetCookieHeaderSpec extends WordSpec with Matchers with HeaderParserHelper[`Set-Cookie`] {
  def hparse(value: String): Validation[ParseErrorInfo, `Set-Cookie`] = HttpParser.SET_COOKIE(value)

  "Set-Cookie parser" should {

//    case class Cookie(
//                       name: String,
//                       content: String,
//                       expires: Option[DateTime] = None,
//                       maxAge: Option[Long] = None,
//                       domain: Option[String] = None,
//                       path: Option[String] = None,
//                       secure: Boolean = false,
//                       httpOnly: Boolean = false,
//                       extension: Option[String] = None


    val cookiestr = "myname=\"foo\"; Domain=value; Max-Age=1; Path=value; Secure;HttpOnly"

    "parse a set cookie" in {
      val c = parse(cookiestr).cookie
      c.name should equal("myname")
      c.content should equal("foo")
      c.maxAge should equal(Some(1))
      c.path should equal(Some("value"))
      c.secure should equal(true)
      c.httpOnly should equal(true)

    }

  }
}

class CookieHeaderSpec extends WordSpec with Matchers with HeaderParserHelper[Header.Cookie] {
  def hparse(value: String): Validation[ParseErrorInfo, Header.Cookie] = HttpParser.COOKIE(value)

  val cookiestr = "key1=value1; key2=\"value2\""
  val cookies = Seq(Cookie("key1", "value1"), Cookie("key2", "value2"))

  "Cookie parser" should {
    "parse a cookie" in {

      parse(cookiestr).values.list should equal (cookies)

    }
  }
}
