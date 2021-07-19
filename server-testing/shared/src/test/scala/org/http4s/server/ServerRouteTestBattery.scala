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

package org.http4s.server

import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.HttpApp
import org.http4s.Method
import org.http4s.Response
import org.http4s.Status
import org.http4s.client.ClientRouteTestBattery
import org.http4s.client.testroutes.GetRoutes

abstract class ServerRouteTestBattery(name: String) extends ClientRouteTestBattery(name) {

  def serverResource(app: HttpApp[IO]): Resource[IO, Server]

  override final def serverResource: Resource[IO, Server] = serverResource(HttpApp[IO] { request =>
    val get = Some(request).filter(_.method == Method.GET).flatMap { r =>
      GetRoutes.getPaths.get(r.uri.path.toString)
    }

    val post = Some(request).filter(_.method == Method.POST).map { r =>
      IO(Response(body = r.body))
    }

    get.orElse(post).getOrElse(IO(Response[IO](status = Status.NotFound)))
  })

}
