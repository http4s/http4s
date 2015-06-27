package org.http4s
package server


import org.http4s.server.MockServer._
import scodec.bits.ByteVector

import scalaz.concurrent.Task

class MockServer(service: HttpService) {

  def apply(request: Request): Task[MockResponse] = {
    val task = for {
      response <- try service(request) catch {
                    case _: Throwable  => Task.now(Response(Status.InternalServerError))
                  }
      body <- response.body.collect{ case c: ByteVector => c.toArray }.runLog
    } yield MockResponse(
      response.status,
      response.headers,
      body.flatten.toArray,
      response.attributes
    )

    task.handle {
      case e =>
        e.printStackTrace()
        MockResponse(Status.InternalServerError)
    }
  }
}

object MockServer {
  private[MockServer] val emptyBody = Array.empty[Byte]   // Makes direct Response comparison possible

  case class MockResponse(
                           statusLine: Status = Status.Ok,
                           headers: Headers = Headers.empty,
                           body: Array[Byte] = emptyBody,
                           attributes: AttributeMap = AttributeMap.empty
                           )
}