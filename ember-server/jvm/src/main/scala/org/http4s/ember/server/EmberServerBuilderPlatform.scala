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

package org.http4s.ember.server

import cats.effect.Async
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.comcast.ip4s.SocketAddress
import com.comcast.ip4s.IpAddress
import org.http4s.server.Server

private[server] trait EmberServerBuilderCompanionPlatform {

  private[server] def defaultLogger[F[_]: Async]: Logger[F] = Slf4jLogger.getLogger[F]

  private[server] def mkServer(bindAddress: SocketAddress[IpAddress], secure: Boolean): Server =
    new Server {
      val address = bindAddress.toInetSocketAddress
      val isSecure = secure
    }

}
