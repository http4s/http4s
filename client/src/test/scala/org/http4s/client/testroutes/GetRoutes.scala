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
import fs2._
import org.http4s.Status._

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"
  val DelayedPath = "/delayed"
  val NoContentPath = "/no-content"
  val NotFoundPath = "/not-found"
  val EmptyNotFoundPath = "/empty-not-found"
  val InternalServerErrorPath = "/internal-server-error"

  def getPaths: Map[String, Response[IO]] =
    Map(
      SimplePath -> Response[IO](Ok).withEntity("simple path"),
      ChunkedPath -> Response[IO](Ok)
        .withEntity(Stream.emits("chunk".toSeq.map(_.toString)).covary[IO]),
      DelayedPath ->
        // TODO This was just getting unsafeRunSynced away...
        // timer.sleep(1.second) *>
        Response[IO](Ok).withEntity("delayed path"),
      NoContentPath -> Response[IO](NoContent),
      NotFoundPath -> Response[IO](NotFound).withEntity("not found"),
      EmptyNotFoundPath -> Response[IO](NotFound),
      InternalServerErrorPath -> Response[IO](InternalServerError)
    )
}
