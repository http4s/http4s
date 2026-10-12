/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.Async
import cats.effect.Resource
import com.comcast.ip4s._
import fs2.io.net.Network

private[wire] object WirePeerTransport {
  final case class Listener[F[_]](
      address: SocketAddress[IpAddress],
      accept: Resource[F, RawPeer[F]],
  )

  def connect[F[_]: Async: Network](
      address: SocketAddress[IpAddress],
      transcript: Transcript[F],
  ): Resource[F, RawPeer[F]] =
    Network[F].connect(address).evalMap(RawPeer.create(_, transcript))

  def bind[F[_]: Async: Network](transcript: Transcript[F]): Resource[F, Listener[F]] =
    Network[F].bind(SocketAddress(ip"127.0.0.1", port"0")).map { listener =>
      Listener(
        listener.address.asIpUnsafe,
        listener.accept.head.compile.resource.lastOrError.evalMap(RawPeer.create(_, transcript)),
      )
    }
}
