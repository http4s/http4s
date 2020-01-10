package org.http4s

import cats.effect.{IO, Timer}
import cats.effect.testing.specs2.CatsIO

class HttpDateSpec extends Http4sSpec with CatsIO {
  override val timer: Timer[IO] = Http4sSpec.TestTimer
  "HttpDate" should {
    "current should be extremely close to now" >> {
      HttpDate.current[IO].map { current =>
        val diff = HttpDate.now.epochSecond - current.epochSecond
        (diff must be_===(0L)).or(be_===(1L))
      }
    }
  }
}
