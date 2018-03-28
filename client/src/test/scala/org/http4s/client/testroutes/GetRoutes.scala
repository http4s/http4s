package org.http4s
package client.testroutes

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.Status._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"
  val DelayedPath = "/delayed"

  def getPaths(implicit ec: ExecutionContext): Map[String, Response[IO]] =
    Map(
      SimplePath -> Response[IO](Ok).withBody("simple path"),
      ChunkedPath -> Response[IO](Ok).withBody(
        Stream.emits("chunk".toSeq.map(_.toString)).covary[IO]),
      DelayedPath ->
        IO.sleep(1.seconds) *> Response[IO](Ok).withEntity("delayed path").pure[IO]
    ).mapValues(_.unsafeRunSync())
}
