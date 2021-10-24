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

import org.http4s.client.Client
import org.http4s.Request
import cats.effect.kernel.MonadCancelThrow

/** Middleware to direct all requests to the provided `UnixSocketAddress` */
object UnixSocket {
  def apply[F[_]: MonadCancelThrow](address: fs2.io.net.unixsocket.UnixSocketAddress)(
      client: Client[F]): Client[F] =
    Client(req => client.run(req.withAttribute(Request.Keys.UnixSocketAddress, address)))
}
