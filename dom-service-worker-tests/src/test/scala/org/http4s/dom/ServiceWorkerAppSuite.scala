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

package org.http4s.dom

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Hostname
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import org.http4s.HttpApp
import org.http4s.client.Client
import org.http4s.server.Server
import org.http4s.server.ServerRouteTestBattery
import org.scalajs.dom.experimental.serviceworkers._
import org.scalajs.dom.raw.Event
import org.scalajs.dom.window

import scala.scalajs.js

class ServiceWorkerAppSuite extends ServerRouteTestBattery("ServiceWorkerApp") {

  def scalaVersion = if (BuildInfo.scalaVersion.startsWith("2"))
    BuildInfo.scalaVersion.split("\\.").init.mkString(".")
  else
    BuildInfo.scalaVersion

  override def clientResource: Resource[IO, Client[IO]] = Resource.pure(FetchClient[IO])

  override def serverResource(app: HttpApp[IO]): Resource[IO, Server] =
    Resource
      .eval {
        IO.fromPromise {
          IO {
            window.navigator.serviceWorker.register(
              s"/dom-service-worker-tests/.js/target/scala-${scalaVersion}/http4s-dom-service-worker-tests-fastopt/main.js",
              js.Dynamic.literal(scope = "/")
            )
          }
        }
      }
      .evalMap { _ =>
        IO.async_[Unit] { cb =>
          window.navigator.serviceWorker
            .addEventListener[Event]("controllerchange", (_: Event) => cb(Right(())))
        }
      }
      .as {
        new Server {
          override def address: SocketAddress[Host] =
            SocketAddress(Hostname.fromString("localhost").get, Port.fromInt(8889).get)
          override def isSecure: Boolean = false
        }
      }

}
