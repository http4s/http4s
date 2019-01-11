package org.http4s
package testing

import cats.effect.IO
import org.http4s.headers._

class ResponseGeneratorSpec extends Http4sSpec {
  "haveHeaders" should {
    "work on value equality" in {
      val resp = Response[IO]().withEntity("test")
      resp must
        haveHeaders(
          Headers(
            `Content-Length`.unsafeFromLong(4L),
            `Content-Type`(MediaType.text.`plain`, Charset.`UTF-8`)
          ))
    }
  }
}
