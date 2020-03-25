package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.{HashTests, OrderTests}
import org.http4s.Uri.Ipv4Address
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer.renderString
import org.specs2.execute._, Typecheck._
import org.specs2.matcher.TypecheckMatchers._

class Ipv4AddressSpec extends Http4sSpec {
  checkAll("Order[Ipv4Address]", OrderTests[Ipv4Address].order)
  checkAll("Hash[Ipv4Address]", HashTests[Ipv4Address].hash)
  checkAll("HttpCodec[Ipv4Address]", HttpCodecTests[Ipv4Address].httpCodec)

  "render" should {
    "render all 4 octets" in {
      renderString(ipv4"192.168.0.1") must_== "192.168.0.1"
    }
  }

  "fromInet4Address" should {
    "round trip with toInet4Address" in prop { (ipv4: Ipv4Address) =>
      Ipv4Address.fromInet4Address(ipv4.toInet4Address) must_== ipv4
    }
  }

  "fromByteArray" should {
    "round trip with toByteArray" in prop { (ipv4: Ipv4Address) =>
      Ipv4Address.fromByteArray(ipv4.toByteArray) must_== Right(ipv4)
    }
  }

  "compare" should {
    "be consistent with unsigned int" in prop { (xs: List[Ipv4Address]) =>
      def tupled(a: Ipv4Address) = (a.a, a.b, a.c, a.d)
      xs.sorted.map(tupled) must_== xs.map(tupled).sorted
    }

    "be consistent with Ordered" in prop { (a: Ipv4Address, b: Ipv4Address) =>
      math.signum(a.compareTo(b)) must_== math.signum(a.compare(b))
    }
  }

  "ipv4 interpolator" should {
    "be consistent with fromString" in {
      Right(ipv4"127.0.0.1") must_== Ipv4Address.fromString("127.0.0.1")
      Right(ipv4"192.168.0.1") must_== Ipv4Address.fromString("192.168.0.1")
    }

    "reject invalid values" in {
      typecheck("""ipv4"256.0.0.0"""") must not succeed
    }
  }
}
