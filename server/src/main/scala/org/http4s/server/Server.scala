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

import java.net.{Inet4Address, Inet6Address, InetSocketAddress}
import org.log4s.getLogger

abstract class Server[F[_]] {
  private[this] val logger = getLogger

  def address: InetSocketAddress

  def isSecure: Boolean

  def baseUri: Uri =
    Uri(
      scheme = Some(if (isSecure) Uri.Scheme.https else Uri.Scheme.http),
      authority = Some(
        Uri.Authority(
          host = address.getAddress match {
            case ipv4: Inet4Address =>
              Uri.Ipv4Address.fromInet4Address(ipv4)
            case ipv6: Inet6Address =>
              Uri.Ipv6Address.fromInet6Address(ipv6)
            case weird =>
              logger.warn(s"Unexpected address type ${weird.getClass}: $weird")
              Uri.RegName(weird.getHostAddress)
          },
          port = Some(address.getPort)
        )),
      path = "/"
    )
}
