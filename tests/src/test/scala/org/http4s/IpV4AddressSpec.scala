package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.{HashTests, OrderTests}
import org.http4s.Uri.IpV4Address
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer.renderString
import org.specs2.execute._, Typecheck._
import org.specs2.matcher.TypecheckMatchers._

class IpV4AddressSpec extends Http4sSpec {
  checkAll("Order[IpV4Address]", OrderTests[IpV4Address].order)
  checkAll("Hash[IpV4Address]", HashTests[IpV4Address].hash)
  checkAll("HttpCodec[IpV4Address]", HttpCodecTests[IpV4Address].httpCodec)

  "render" should {
    "render all 4 octets" in {
      renderString(ipV4"192.168.0.1") must_== "192.168.0.1"
    }
  }

  "fromInet4Address" should {
    "round trip with toInet4Address" in prop { ipv4: IpV4Address =>
      IpV4Address.fromInet4Address(ipv4.toInet4Address) must_== ipv4
    }
  }

  "fromByteArray" should {
    "round trip with toByteArray" in prop { ipv4: IpV4Address =>
      IpV4Address.fromByteArray(ipv4.toByteArray) must_== Right(ipv4)
    }
  }

  "compare" should {
    "be consistent with unsigned int" in prop { xs: List[IpV4Address] =>
      def tupled(a: IpV4Address) = (a.a, a.b, a.c, a.d)
      xs.sorted.map(tupled) must_== xs.map(tupled).sorted
    }

    "be consistent with Ordered" in prop { (a: IpV4Address, b: IpV4Address) =>
      math.signum(a.compareTo(b)) must_== math.signum(a.compare(b))
    }
  }

  "ipV4 interpolator" should {
    "be consistent with fromString" in {
      Right(ipV4"127.0.0.1") must_== IpV4Address.fromString("127.0.0.1")
      Right(ipV4"192.168.0.1") must_== IpV4Address.fromString("192.168.0.1")
    }

    "reject invalid values" in {
      typecheck("""ipV4"256.0.0.0"""") must not succeed
    }
  }
}
