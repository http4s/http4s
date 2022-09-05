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

abstract class Server {
  private[server] val logger = Platform.loggerFactory.getLogger

  def address: ip4s.SocketAddress[ip4s.IpAddress]
  final def addressIp4s: ip4s.SocketAddress[ip4s.IpAddress] = address

  def baseUri: Uri =
    Uri(
      scheme = Some(if (isSecure) Uri.Scheme.https else Uri.Scheme.http),
      authority = Some(
        Uri.Authority(
          host = address.host match {
            case ipv4: ip4s.Ipv4Address =>
              Uri.Ipv4Address(ipv4)
            case ipv6: ip4s.Ipv6Address =>
              Uri.Ipv6Address(ipv6)
          },
          port = Some(address.port.value),
        )
      ),
      path = Uri.Path.Root,
    )

  def isSecure: Boolean
}
