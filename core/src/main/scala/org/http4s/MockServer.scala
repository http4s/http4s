package org.http4s

import scala.language.reflectiveCalls
import concurrent.{Await, ExecutionContext, Future}
import concurrent.duration._
import scala.language.higherKinds
import scalaz.stream.Process
import scalaz.Monad
import scalaz.Catchable
import scala.util.control.NonFatal

class MockServer[F[_]](service: HttpService[F]) {
  import MockServer._

  def apply(request: Request[F])(implicit F: Monad[F], C: Catchable[F]): F[MockResponse] = {
    val process = for {
      request <- Process.emit(request)
      response <- service(request)
      body <- response.body.toMonoid
    } yield MockResponse(
      response.prelude.status,
      response.prelude.headers,
      body.bytes.toArray
    )
    process.handle {
      case NonFatal(e) =>
        e.printStackTrace()
        Process.emit(MockResponse(Status.InternalServerError))
    }.runLastOr(MockResponse(Status.NotFound))
  }
}

object MockServer {
  private[MockServer] val emptyBody = Array.empty[Byte]   // Makes direct Response comparison possible

  case class MockResponse(
    statusLine: Status = Status.Ok,
    headers: HttpHeaders = HttpHeaders.Empty,
    body: Array[Byte] = emptyBody
  )
}
