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

package org.http4s
package client

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import org.http4s.node.serverless.ServerlessApp

import scala.scalajs.js

object ServerScaffold {
  val http = js.Dynamic.global.require("http")

  def apply[F[_]](num: Int, secure: Boolean, routes: HttpRoutes[F])(implicit
      F: Async[F]
  ): Resource[F, ServerScaffold] = {
    require(num == 1 && !secure)
    Dispatcher[F].flatMap { dispatcher =>
      Resource
        .make {
          F.delay(
            http.createServer(
              Function.untupled(
                ServerlessApp(routes.orNotFound).tupled.andThen(dispatcher.unsafeRunAndForget)
              )
            )
          )
        } { server =>
          F.async_[Unit] { cb => server.close(() => cb(Right(()))); () }
        }
        .evalMap { server =>
          F.async_[ServerScaffold] { cb =>
            server.listen { () =>
              cb(
                Right(
                  ServerScaffold(
                    List(
                      SocketAddress(
                        IpAddress.fromString(server.address().address.asInstanceOf[String]).get,
                        Port.fromInt(server.address().port.asInstanceOf[Int]).get,
                      )
                    )
                  )
                )
              )
            }
            ()
          }
        }
    }

  }
}

final case class ServerScaffold(addresses: List[SocketAddress[IpAddress]])
