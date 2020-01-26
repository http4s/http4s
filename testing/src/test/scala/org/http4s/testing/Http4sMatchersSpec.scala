package org.http4s
package testing

import cats.effect.IO
import cats.implicits._
import org.http4s.headers._
import org.http4s.testing.Http4sSyncSpec

class Http4sMatchersSpec extends Http4sSyncSpec {
  "haveHeaders" - {
    "work on value equality" in {
      val resp = Response[IO]().withEntity("test")
      assert(
        resp.headers === Headers.of(
          `Content-Length`.unsafeFromLong(4L),
          `Content-Type`(MediaType.text.`plain`, Charset.`UTF-8`)
        ))
    }
  }
}
