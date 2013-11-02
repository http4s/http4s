package org.http4s

import scalaz.stream.Process
import scalaz.{\/-, -\/, Monad, Catchable}
import scala.util.control.NonFatal
import scalaz.concurrent.Task

class MockServer(service: HttpService) {
  import MockServer._

  def apply(request: Request): Task[MockResponse] = {
    val task = for {
      response <- service(request)
      body <- response.body.scanSemigroup.toTask
    } yield MockResponse(
      response.prelude.status,
      response.prelude.headers,
      body.toArray
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
    headers: HeaderCollection = HeaderCollection.empty,
    body: Array[Byte] = emptyBody
  )
}
