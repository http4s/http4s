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

import cats.ApplicativeThrow
import cats.effect.Resource
import com.comcast.ip4s._

/** An injected server handle; scripts and observation resources keep their own lifetimes. */
private[wire] object ScriptedServer {
  def apply[F[_]: ApplicativeThrow](
      label: String,
      handle: Resource[F, ServerHandle[F]],
  ): WireTarget[F] =
    new WireTarget[F] {
      val name = label
      val version = "oracle-challenge"
      def withFailureObserver(observe: AdapterFailure => F[Unit]): WireTarget[F] = this
      def server(protocol: Protocol, scenario: ServerScenario): Resource[F, ServerHandle[F]] =
        handle
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[F, ClientDriver[F]] =
        Resource.eval(
          ApplicativeThrow[F].raiseError(new UnsupportedOperationException("server only"))
        )
    }
}
