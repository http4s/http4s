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

package org.http4s.ember.client.internal

import com.comcast.ip4s.Host
import com.comcast.ip4s.SocketAddress
import fs2.io.net.tls.TLSParameters
import com.comcast.ip4s.Hostname
import scala.annotation.tailrec
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.IDN

import scala.annotation.nowarn

private[internal] trait ClientHelpersPlatform {

  @nowarn("cat=unused")
  private[internal] def mkTLSParameters(
      address: Option[SocketAddress[Host]],
      enableEndpointValidation: Boolean): TLSParameters =
    TLSParameters(
      servername = address.map(a => extractHostname(a.host))
    ) // TODO how to enable endpoint validation?

  @tailrec
  private def extractHostname(from: Host): String = from match {
    case hostname: Hostname => hostname.normalized.toString
    case address: IpAddress => address.toString
    case idn: IDN => extractHostname(idn.hostname)
  }
}
