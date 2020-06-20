/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

/** Tests for the definitions in Cookie.scala */
final class CookieSpec extends Http4sSpec {
  "RequestCookieJar" should {
    "Not duplicate elements when adding the empty set" in {
      val jar = RequestCookieJar(RequestCookie("foo", "bar"))
      (jar ++ Set()).## must_== jar.##
    }
  }
}
