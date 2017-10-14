package org.http4s

import cats.kernel.laws.OrderLaws
import org.http4s.Uri.Port
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.testing.HttpCodecTests
import org.http4s.util.Renderer

class PortSpec extends Http4sSpec {
  "compare" should {
    "be consistent with compare of the int value" in prop { (a: Port, b: Port) =>
      a.compare(b) must_== a.toInt.compare(b.toInt)
    }
  }

  "render" should {
    "return int value" in prop { p: Port =>
      Renderer.renderString(p) must_== p.toInt.toString
    }
  }

  "parse" should {
    "reject all invalid ports" in { s: String =>
      (s.isEmpty || !s.forall(CharPredicate.Digit) || s.toInt < 0 || s.toInt > 65535) ==>
        (Port.parse(s) must beLeft)
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      port"80" must_== Port.http
    }

    "reject invalid literals" in {
      illTyped("""port"нет"""")
      illTyped("""port"65536"""")
      true
    }
  }

  "fromInt" should {
    "be consistent with toInt" in prop { port: Port =>
      Port.fromInt(port.toInt) == Right(port)
    }
  }

  checkAll("Order[Port]", OrderLaws[Port].order)
  checkAll("Order[HttpCodec]", HttpCodecTests[Port].httpCodec)
}
