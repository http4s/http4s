package org.http4s.parser

import org.http4s.Header.{`Set-Cookie`}
import org.specs2.mutable.Specification
import scalaz.Validation
import org.http4s.{Header, Cookie}

class SetCookieHeaderSpec extends Specification with HeaderParserHelper[`Set-Cookie`] {
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
      c.name must be_==("myname")
      c.content must be_==("foo")
      c.maxAge must be_==(Some(1))
      c.path must be_==(Some("value"))
      c.secure must be_==(true)
      c.httpOnly must be_==(true)

    }

  }
}

class CookieHeaderSpec extends Specification with HeaderParserHelper[Header.Cookie] {
  def hparse(value: String): Validation[ParseErrorInfo, Header.Cookie] = HttpParser.COOKIE(value)

  val cookiestr = "key1=value1; key2=\"value2\""
  val cookies = Seq(Cookie("key1", "value1"), Cookie("key2", "value2"))

  "Cookie parser" should {
    "parse a cookie" in {

      parse(cookiestr).values.list must be_== (cookies)

    }
  }
}
