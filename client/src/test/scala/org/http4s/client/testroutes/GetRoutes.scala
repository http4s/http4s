package org.http4s.client.testroutes

import cats.effect._
import fs2.Stream.emits
import org.http4s.Response
import org.http4s.Status._

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"

  val getPaths: Map[String, Response[IO]] = {
    Map(
      SimplePath -> Response[IO](Ok).withBody("simple path"),
      ChunkedPath -> Response[IO](Ok).withBody(emits[IO, String]("chunk".toSeq.map(_.toString)))
    ).mapValues(_.unsafeRunSync())
  }
}
