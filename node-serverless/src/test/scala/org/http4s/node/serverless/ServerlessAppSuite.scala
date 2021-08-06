/*
 * Copyright 2021 http4s.org
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
package node.serverless

import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Server
import org.http4s.server.ServerRouteTestBattery

import scala.scalajs.js

class ServerlessAppSuite extends ServerRouteTestBattery("ServerlessApp") {

  override def clientResource: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

  val http = js.Dynamic.global.require("http")

  def serverResource(app: HttpApp[IO]): Resource[IO, Server] =
    Resource
      .make {
        IO(http.createServer(ServerlessApp.unsafeExportApp(app)))
      } { server =>
        IO.async_[Unit](cb => server.close(() => cb(Right(()))))
      }
      .evalMap { server =>
        IO.async_[Server] { cb =>
          server.listen { () =>
            cb(Right(new Server {
              override def address: SocketAddress[Host] =
                SocketAddress(
                  Host.fromString(server.address().address.asInstanceOf[String]).get,
                  Port.fromInt(server.address().port.asInstanceOf[Int]).get
                )

              override def isSecure: Boolean = false
            }))
          }
        }
      }

}
