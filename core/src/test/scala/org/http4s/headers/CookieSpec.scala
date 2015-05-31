package org.http4s
package headers

class SetCookieHeaderSpec extends HeaderParserSpec(`Set-Cookie`) {

  "Set-Cookie parser" should {

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

class CookieHeaderSpec extends HeaderParserSpec(headers.Cookie) {

  val cookiestr = "key1=value1; key2=\"value2\""
  val cookies = Seq(org.http4s.Cookie("key1", "value1"), org.http4s.Cookie("key2", "value2"))

  "Cookie parser" should {
    "parse a cookie" in {
      parse(cookiestr).values.list must be_== (cookies)
    }
  }
}
