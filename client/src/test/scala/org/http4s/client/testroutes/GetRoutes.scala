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

  def getPaths(implicit timer: Timer[IO]): Map[String, Response[IO]] =
    Map(
      SimplePath -> Response[IO](Ok).withEntity("simple path").pure[IO],
      ChunkedPath -> Response[IO](Ok)
        .withEntity(Stream.emits("chunk".toSeq.map(_.toString)).covary[IO])
        .pure[IO],
      DelayedPath ->
        timer.sleep(1.second) *>
          Response[IO](Ok).withEntity("delayed path").pure[IO]
    ).mapValues(_.unsafeRunSync())
}
