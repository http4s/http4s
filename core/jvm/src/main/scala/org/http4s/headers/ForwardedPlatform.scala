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

package org.http4s.headers

import java.net.Inet4Address
import java.net.Inet6Address
import Forwarded.Node.Name
import Forwarded.Node.Name.Ipv4
import Forwarded.Node.Name.Ipv6
import com.comcast.ip4s.Ipv4Address
import com.comcast.ip4s.Ipv6Address

private[headers] trait NameCompanionPlatform {
  def ofInet4Address(address: Inet4Address): Name =
    Ipv4(Ipv4Address.fromInet4Address(address))

  def ofInet6Address(address: Inet6Address): Name =
    Ipv6(Ipv6Address.fromInet6Address(address))
}
