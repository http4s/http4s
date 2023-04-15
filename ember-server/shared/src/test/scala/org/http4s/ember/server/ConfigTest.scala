package org.http4s.ember.server

import com.comcast.ip4s.{Host, Port}
import com.typesafe.config.ConfigFactory
import org.http4s.ember.server.Config.{TLSConfig, UnixSocketConfig}
import fs2.io.net.unixsocket.UnixSocketAddress
import org.http4s.Http4sSuite
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._
import pureconfig.syntax._

class ConfigTest extends Http4sSuite {

  implicit lazy val readerTLSConfig: ConfigReader[TLSConfig] =
    deriveReader

  implicit lazy val readerUnixSocketAddress: ConfigReader[UnixSocketAddress] =
    ConfigReader[String].map(UnixSocketAddress(_))

  implicit lazy val readerUnixSocketConfig: ConfigReader[UnixSocketConfig] =
    deriveReader

  implicit lazy val readerHost: ConfigReader[Host] =
    ConfigReader[String].emap { value =>
      Host.fromString(value).toRight(CannotConvert(s"$value", "Host", "cannot convert"))
    }

  implicit lazy val readerPort: ConfigReader[Port] = {
    val int = ConfigReader[Int]
      .emap { value =>
        Port.fromInt(value).toRight(CannotConvert(s"$value", "Port", "cannot convert"))
      }
    val string = ConfigReader[String].emap { value =>
      Port.fromString(value).toRight(CannotConvert(s"$value", "Port", "cannot convert"))
    }
    int.orElse(string)
  }

  implicit lazy val reader: ConfigReader[Config] =
    deriveReader

  test("Creates config") {
    val conf = ConfigFactory.parseString(s"""{
      |    port = 1234
      |}""".stripMargin)
    val Right(res) = conf.to[Config]
    assertEquals(res.port, Port.fromInt(1234).get)
    assertEquals(res.maxConnections, 1024)
  }

}
