package org.http4s
package client.testroutes

import cats.effect._
import cats.implicits._
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

  def getPaths(implicit timer: Timer[IO]): Map[String, Response[IO]] =
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
    ).mapValues(_.unsafeRunSync())
}
