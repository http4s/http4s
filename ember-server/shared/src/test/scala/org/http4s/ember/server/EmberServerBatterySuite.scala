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

package org.http4s
package ember.server

import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s.Port
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Server
import org.http4s.server.ServerRouteTestBattery

class EmberServerBatterySuite extends ServerRouteTestBattery("EmberServer") {

  override def clientResource: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

  override def serverResource(app: HttpApp[IO]): Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHttpApp(app)
    .withPort(Port.fromInt(org.http4s.server.defaults.HttpPort + 1).get)
    .build

}
