/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.server

import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.typesafe.config.ConfigFactory
import fs2.io.net.unixsocket.UnixSocketAddress
import org.http4s.Http4sSuite
import org.http4s.ember.server.Config.TLSConfig
import org.http4s.ember.server.Config.UnixSocketConfig
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
