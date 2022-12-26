package org.http4s.server.middleware

import org.http4s._
import org.http4s.headers.From

class FromSuite extends Http4sSuite {
  test("should parse the email address") {
    val cases = List(
      ("a@example.com", "a@example.com"),
      ("a.b@example.com", "a.b@example.com"),
      ("display name <a@b.com>", "a@b.com"),
      ("\"abc!\"@example.com", "abc!@example.com"),
      ("(comment A) \r\n (comment B) a@b.com", "a@b.com"),
    )
    cases.foreach(c => {
      val result = From.parse(c._1)
      assertEquals(result.toOption.get, From(c._2))
    })

    val failCases = List(
      "a.b.com",
      "display name a@b.com",
    )
    failCases.foreach(c => {
      val result =From.parse(c)
      assert(result.isLeft)
    })
  }
}
