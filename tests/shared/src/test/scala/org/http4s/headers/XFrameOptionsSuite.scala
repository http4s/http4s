package org.http4s
package headers

import org.http4s.implicits.http4sSelectSyntaxOne

class XFrameOptionsSuite extends Http4sSuite {

  test("render should create and header with the correct value") {
    assertEquals(
      `X-Frame-Options`.parse("\"deny\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: DENY"),
    )

    assertEquals(
      `X-Frame-Options`.parse("\"sameorigin\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: SAMEORIGIN"),
    )
  }

  test("parse should fail on a header with invalid value") {
    assert(`X-Frame-Options`.parse("\"invalid\"").map(_.renderString).isLeft)
  }

  test("parse should be case insensitive") {
    assertEquals(
      `X-Frame-Options`.parse("\"DEnY\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: DENY"),
    )

    assertEquals(
      `X-Frame-Options`.parse("\"SAMeORIGIN\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: SAMEORIGIN"),
    )
  }
}
