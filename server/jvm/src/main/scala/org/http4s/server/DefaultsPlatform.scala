/*
 * Copyright 2014 http4s.org
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

package org.http4s.server

import java.net.InetAddress
import java.net.InetSocketAddress

private[server] trait DefaultsPlatform { self: defaults.type =>

  val IPv4Host: String =
    InetAddress.getByAddress("localhost", Array[Byte](127, 0, 0, 1)).getHostAddress
  val IPv6Host: String =
    InetAddress
      .getByAddress("localhost", Array(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
      .getHostAddress

  @deprecated(
    message =
      "Please use IPv4Host or IPv6Host. This value can change depending on Platform specific settings and can be either the canonical IPv4 or IPv6 address. If you require this behavior please call `InetAddress.getLoopbackAddress` directly.",
    since = "0.21.23",
  )
  val Host = InetAddress.getLoopbackAddress.getHostAddress

  val IPv4SocketAddress: InetSocketAddress =
    InetSocketAddress.createUnresolved(IPv4Host, HttpPort)
  val IPv6SocketAddress: InetSocketAddress =
    InetSocketAddress.createUnresolved(IPv6Host, HttpPort)

  @deprecated(
    message =
      "Please use IPv4SocketAddress or IPv6SocketAddress. This value can change depending on Platform specific settings and can be either the canonical IPv4 or IPv6 address. If you require this behavior please call `InetAddress.getLoopbackAddress` directly.",
    since = "0.21.23",
  )
  val SocketAddress = InetSocketAddress.createUnresolved(Host, HttpPort)

}
