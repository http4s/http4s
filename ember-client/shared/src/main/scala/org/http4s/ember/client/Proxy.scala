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

package org.http4s.ember.client

import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.BasicCredentials
import org.http4s.Uri

/** A proxy through which Ember tunnels connections to origin servers. */
sealed trait Proxy extends Product with Serializable

final case class HttpProxy(
    scheme: Uri.Scheme,
    host: Host,
    port: Option[Port] = None,
    credentials: Option[BasicCredentials] = None,
) extends Proxy

final case class Socks4Proxy(host: Host, port: Port, username: Option[String] = None) extends Proxy

final case class Socks5Credentials(username: String, password: String)

final case class Socks5Proxy(host: Host, port: Port, credentials: Option[Socks5Credentials] = None)
    extends Proxy
