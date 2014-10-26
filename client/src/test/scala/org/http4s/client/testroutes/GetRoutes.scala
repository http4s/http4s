package org.http4s.client.testroutes

import org.http4s.Status._
import org.http4s.{TransferCoding, ResponseBuilder, Response}

import scalaz.stream.Process

trait GetRoutes {

  /////////////// Test routes for clients ////////////////////////////////

  protected val getPaths: Map[String, Response] = {
    import org.http4s.Header._
    Map(
      "/simple" -> ResponseBuilder(Ok, "simple path").run,
      "/chunked" -> ResponseBuilder(Ok, Process.emit("chunk1"), `Transfer-Encoding`(TransferCoding.chunked)).run
    )
  }

}
