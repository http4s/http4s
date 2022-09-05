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

package org.http4s
package server

import com.comcast.ip4s

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress

abstract class Server {
  private[this] val logger = Platform.loggerFactory.getLogger

  def address: InetSocketAddress
  def addressIp4s: ip4s.SocketAddress[ip4s.IpAddress] =
    ip4s.SocketAddress.fromInetSocketAddress(address)

  def isSecure: Boolean

  def baseUri: Uri =
    Uri(
      scheme = Some(if (isSecure) Uri.Scheme.https else Uri.Scheme.http),
      authority = Some(
        Uri.Authority(
          host = address.getAddress match {
            case ipv4: Inet4Address =>
              Uri.Ipv4Address(ip4s.Ipv4Address.fromInet4Address(ipv4))
            case ipv6: Inet6Address =>
              Uri.Ipv6Address(ip4s.Ipv6Address.fromInet6Address(ipv6))
            case weird =>
              logger.warn(s"Unexpected address type ${weird.getClass}: $weird").unsafeRunSync()
              Uri.RegName(weird.getHostAddress)
          },
          port = Some(address.getPort),
        )
      ),
      path = Uri.Path.Root,
    )
}
