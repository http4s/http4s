package org.http4s

import cats.kernel.laws.discipline.OrderTests
import org.http4s.testing.HttpCodecTests
import cats.implicits._

class MediaRangeSpec extends Http4sSpec {
  checkAll("Order[MediaRange]", OrderTests[MediaRange].order)
  checkAll("HttpCodec[MediaRange]", HttpCodecTests[MediaRange].httpCodec)
}
