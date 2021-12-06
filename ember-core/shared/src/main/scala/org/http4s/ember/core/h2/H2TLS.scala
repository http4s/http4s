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

package org.http4s.ember.core.h2

import cats._
import cats.syntax.all._
import fs2.io.net.tls.TLSSocket

private[ember] object H2TLS extends H2TLSPlatform {

  def protocol[F[_]: MonadThrow](tlsSocket: TLSSocket[F]): F[Option[String]] =
    tlsSocket.applicationProtocol
      .map(Option(_))
      .handleErrorWith {
        case _: NoSuchElementException => Option.empty.pure[F]
        case e => e.raiseError[F, Option[String]]
      }

}
