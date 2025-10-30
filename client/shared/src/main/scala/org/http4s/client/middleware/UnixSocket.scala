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

package org.http4s.client.middleware

import cats.effect.kernel.MonadCancelThrow
import com.comcast.ip4s.UnixSocketAddress
import fs2.io.net.unixsocket.{UnixSocketAddress => DeprecatedUnixSocketAddress}
import org.http4s.Request
import org.http4s.client.Client

/** Middleware to direct all requests to the provided `UnixSocketAddress`. */
object UnixSocket {
  @deprecated("Use overload that takes a com.comcast.ip4s.UnixSocketAddress", "0.23.32")
  def apply[F[_]: MonadCancelThrow](address: DeprecatedUnixSocketAddress)(
      client: Client[F]
  ): Client[F] =
    apply(UnixSocketAddress(address.path))(client)

  def apply[F[_]: MonadCancelThrow](address: UnixSocketAddress)(
      client: Client[F]
  ): Client[F] =
    Client(req => client.run(req.withAttribute(Request.Keys.ForcedUnixSocketAddress, address)))
}
