package org.http4s
package headers

import org.http4s.laws.discipline.arbitrary._

class AcceptCHSuite extends HeaderLaws {
  checkAll("Accept-CH", headerLaws[`Accept-CH`])

  test("parse should fail with invalid header tokens") {
    assert(`Accept-CH`.parse("!@#$%").isLeft)
  }
  test("parse should succeed with single header") {
    assert(`Accept-CH`.parse("Viewport-Width").isRight)
  }
  test("parse should succeed with multiple comma-separated headers") {
    assert(`Accept-CH`.parse("Viewport-Width, Width").isRight)
  }
  test("parse should succeed with multiple headers and no whitespace") {
    assert(`Accept-CH`.parse("Viewport-Width,Width").isRight)
  }
}
