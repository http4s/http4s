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
package client.testroutes

import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s.Status._
import scala.concurrent.duration._

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"
  val DelayedPath = "/delayed"
  val NoContentPath = "/no-content"
  val NotFoundPath = "/not-found"
  val EmptyNotFoundPath = "/empty-not-found"
  val InternalServerErrorPath = "/internal-server-error"

  def getPaths(implicit F: Temporal[IO]): Map[String, IO[Response[IO]]] =
    Map(
      SimplePath -> Response[IO](Ok).withEntity("simple path").pure[IO],
      ChunkedPath -> Response[IO](Ok)
        .withEntity(Stream.emits("chunk".toSeq.map(_.toString)).covary[IO])
        .pure[IO],
      DelayedPath ->
        F.sleep(1.second) *>
        Response[IO](Ok).withEntity("delayed path").pure[IO],
      NoContentPath -> Response[IO](NoContent).pure[IO],
      NotFoundPath -> Response[IO](NotFound).withEntity("not found").pure[IO],
      EmptyNotFoundPath -> Response[IO](NotFound).pure[IO],
      InternalServerErrorPath -> Response[IO](InternalServerError).pure[IO]
    )
}
