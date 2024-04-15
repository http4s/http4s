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
package testkit
package scaffold

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import org.http4s.nodejs.IncomingMessage
import org.http4s.nodejs.ServerResponse
import org.typelevel.scalaccompat.annotation._

import scala.scalajs.js

private[http4s] final case class ServerScaffold[F[_]](addresses: List[SocketAddress[IpAddress]])

private[http4s] object ServerScaffold {

  @js.native
  @js.annotation.JSImport("http", "createServer")
  @nowarn212("cat=unused")
  private def createServer(
      requestListener: js.Function2[IncomingMessage, ServerResponse, Unit]
  ): Server = js.native

  @js.native
  @nowarn212("cat=unused")
  private trait Server extends js.Object {
    def listen(port: Int, host: String, cb: js.Function0[Unit]): Any = js.native
    def address(): Address = js.native
    def close(cb: js.Function0[Unit]): Any = js.native
  }

  @js.native
  private trait Address extends js.Object {
    def address: String = js.native
    def port: Int
  }

  def apply[F[_]](num: Int, secure: Boolean, routes: HttpRoutes[F])(implicit
      F: Async[F]
  ): Resource[F, ServerScaffold[F]] = {
    require(num == 1 && !secure)
    val app = routes.orNotFound
    Dispatcher.parallel[F].flatMap { dispatcher =>
      Resource
        .make {
          F.delay(
            createServer { (req, res) =>
              dispatcher.unsafeRunAndForget(
                req.toRequest.flatMap(app(_)).flatMap(res.writeResponse[F])
              )
            }
          )
        } { server =>
          F.async_[Unit] { cb => server.close(() => cb(Either.unit)); () }
        }
        .evalMap { server =>
          F.async_[ServerScaffold[F]] { cb =>
            server.listen(
              0,
              "localhost",
              () =>
                cb(
                  Right(
                    ServerScaffold(
                      List(
                        SocketAddress(
                          IpAddress.fromString(server.address().address).get,
                          Port.fromInt(server.address().port).get,
                        )
                      )
                    )
                  )
                ),
            )
            ()
          }
        }
    }

  }
}
