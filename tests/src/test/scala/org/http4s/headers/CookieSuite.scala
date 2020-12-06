/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

class CookieSuite extends Http4sSuite {
  test("parse a simple pair") {
    assertEquals(Cookie.parse("k=v"), Right(Cookie(RequestCookie("k", "v"))))
  }

  test("parse a quoted pair") {
    assertEquals(Cookie.parse("""k="v""""), Right(Cookie(RequestCookie("k", """"v""""))))
  }

  test("parse two pairs") {
    assertEquals(
      Cookie.parse("""k1=v1; k2="v2""""),
      Right(Cookie(RequestCookie("k1", "v1"), RequestCookie("k2", """"v2""""))))
  }
}
