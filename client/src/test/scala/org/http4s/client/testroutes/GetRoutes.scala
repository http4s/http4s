/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client.testroutes

import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s.Status._
import org.http4s.internal.CollectionCompat
import scala.concurrent.duration._

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"
  val DelayedPath = "/delayed"
  val NoContentPath = "/no-content"
  val NotFoundPath = "/not-found"
  val EmptyNotFoundPath = "/empty-not-found"
  val InternalServerErrorPath = "/internal-server-error"

  def getPaths(implicit timer: Timer[IO]): Map[String, Response[IO]] =
    CollectionCompat.mapValues(
      Map(
        SimplePath -> Response[IO](Ok).withEntity("simple path").pure[IO],
        ChunkedPath -> Response[IO](Ok)
          .withEntity(Stream.emits("chunk".toSeq.map(_.toString)).covary[IO])
          .pure[IO],
        DelayedPath ->
          timer.sleep(1.second) *>
            Response[IO](Ok).withEntity("delayed path").pure[IO],
        NoContentPath -> Response[IO](NoContent).pure[IO],
        NotFoundPath -> Response[IO](NotFound).withEntity("not found").pure[IO],
        EmptyNotFoundPath -> Response[IO](NotFound).pure[IO],
        InternalServerErrorPath -> Response[IO](InternalServerError).pure[IO]
      ))(_.unsafeRunSync())
}
