package org.http4s
package parser

import org.http4s.headers.`Set-Cookie`
import org.specs2.mutable.Specification

class SetCookieHeaderSpec extends Specification with HeaderParserHelper[`Set-Cookie`] {
  def hparse(value: String): ParseResult[`Set-Cookie`] = HttpHeaderParser.SET_COOKIE(value)

  "Set-Cookie parser" should {
    "parse a set cookie" in {
      val cookiestr =
        "myname=\"foo\"; Domain=value; Max-Age=1; Path=value; SameSite=Strict; Secure;HttpOnly"
      val c = parse(cookiestr).cookie
      c.name must be_==("myname")
      c.content must be_==("foo")
      c.maxAge must be_==(Some(1))
      c.path must beSome("value")
      c.sameSite must be_==(SameSite.Strict)
      c.secure must be_==(true)
      c.httpOnly must be_==(true)
    }

    "default to SameSite=Lax" in {
      val cookiestr = "myname=\"foo\"; Domain=value; Max-Age=1; Path=value"
      val c = parse(cookiestr).cookie
      c.sameSite must be_==(SameSite.Lax)
    }
  }
}

class CookieHeaderSpec extends Specification with HeaderParserHelper[headers.Cookie] {
  def hparse(value: String): ParseResult[headers.Cookie] = HttpHeaderParser.COOKIE(value)

  val cookiestr = "key1=value1; key2=\"value2\""
  val cookiestrSemicolon: String = cookiestr + ";"
  val cookies = Seq(RequestCookie("key1", "value1"), RequestCookie("key2", "value2"))

  "Cookie parser" should {
    "parse a cookie" in {
      parse(cookiestr).values.toList must be_==(cookies)
    }
    "parse a cookie (semicolon at the end)" in {
      parse(cookiestrSemicolon).values.toList must be_==(cookies)
    }
  }
}
