package org.http4s.testing

import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.Assertions
import org.scalatest.freespec.AnyFreeSpec

private[testing] trait Http4sSpec { self: Assertions =>
  override def convertToEqualizer[T](left: T): Equalizer[T] = {
    val _ = left
    sys.error("Intentionally ambiguous implicit for Equalizer")
  }
}

private[http4s] trait Http4sSyncSpec extends AnyFreeSpec with Http4sSpec

private[http4s] trait Http4sAsyncSpec extends AsyncIOSpec with Http4sSpec
