/*
 * Copyright 2013 http4s.org
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

import com.comcast.ip4s
import java.net.{Inet4Address, Inet6Address, InetAddress}
import Uri.{Ipv4Address, Ipv6Address}

private[http4s] trait Ipv4AddressPlatform { self: Uri.Ipv4Address =>
  def toInet4Address: Inet4Address =
    address.toInetAddress
}

private[http4s] trait Ipv4AddressCompanionPlatform {
  def apply(address: ip4s.Ipv4Address): Ipv4Address

  def fromInet4Address(address: Inet4Address): Ipv4Address =
    apply(ip4s.Ipv4Address.fromInet4Address(address))
}

private[http4s] trait Ipv6AddressPlatform { self: Uri.Ipv6Address =>
  def toInetAddress: InetAddress =
    address.toInetAddress
}

private[http4s] trait Ipv6AddressCompanionPlatform {
  def apply(address: ip4s.Ipv6Address): Ipv6Address

  def fromInet6Address(address: Inet6Address): Ipv6Address =
    apply(ip4s.Ipv6Address.fromInet6Address(address))
}
