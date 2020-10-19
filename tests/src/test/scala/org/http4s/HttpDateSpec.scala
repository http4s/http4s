/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.effect.IO
import cats.effect.testing.specs2.CatsEffect

class HttpDateSpec extends Http4sSpec with CatsEffect {
  "HttpDate" should {
    "current should be extremely close to Instant.now" >> {
      HttpDate.current[IO].map { current =>
        val diff =
          HttpDate.unsafeFromInstant(java.time.Instant.now).epochSecond - current.epochSecond
        (diff must be_===(0L)).or(be_===(1L))
      }
    }
  }
}
