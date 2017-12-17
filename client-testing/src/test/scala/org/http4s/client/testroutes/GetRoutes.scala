package org.http4s.client.testroutes

import cats.effect._
import fs2._
import org.http4s.Response
import org.http4s.Status._

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"

  val getPaths: Map[String, Response[IO]] = {
    Map(
      SimplePath -> Response[IO](Ok).withBody("simple path"),
      ChunkedPath -> Response[IO](Ok).withBody(
        Stream.emits("chunk".toSeq.map(_.toString)).covary[IO])
    ).mapValues(_.unsafeRunSync())
  }
}
