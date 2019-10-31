package org.http4s

/** Tests for the definitions in Cookie.scala */
final class CookieSpec extends Http4sSpec {

  "RequestCookieJar" should {

    "Not duplicate elements when adding the empty set" in {
      val jar = new RequestCookieJar(List(RequestCookie("foo", "bar")))
      (jar ++ Set()).## must_== jar.##
    }

  }

}
