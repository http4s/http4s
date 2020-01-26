package org.http4s

import cats.effect.{IO, Timer}

class HttpDateSpec extends Http4sSpec {
  override val timer: Timer[IO] = Http4sSpec.TestTimer
  "HttpDate" should {
    "current should be extremely close to Instant.now" >> {
      HttpDate.current[IO].map { current =>
        val diff = HttpDate.unsafeFromInstant(java.time.Instant.now).epochSecond - current
          .epochSecond
        (diff must be_===(0L)).or(be_===(1L))
      }
    }
  }
}
