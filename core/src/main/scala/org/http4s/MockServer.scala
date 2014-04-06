package org.http4s

import scalaz.concurrent.Task

class MockServer(service: HttpService) {
  import MockServer._

  def apply(request: Request): Task[MockResponse] = {
    val task = for {
      response <- try service(request) catch {
                    case m: MatchError => Status.NotFound()
                    case _: Throwable  => Status.InternalServerError()
                  }
      body <- response.body.collect{ case c: BodyChunk => c.toArray }.runLog
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
