package org.http4s.client.testroutes

import org.http4s.Status._
import org.http4s.{TransferCoding, Response}

import scalaz.stream.Process

object GetRoutes {
  val SimplePath = "/simple"
  val ChunkedPath = "/chunked"

  val getPaths: Map[String, Response] = {
    import org.http4s.headers._
    Map(
      SimplePath -> Response(Ok).withBody("simple path").run,
      ChunkedPath -> Response(Ok).withBody(Process.emit("chunk1")).map(_.putHeaders(`Transfer-Encoding`(TransferCoding.chunked))).run
    )
  }
}
