package org.http4s
package client
package middleware

import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz.stream.Process
import scodec.bits.ByteVector
import org.http4s.dsl._
import org.http4s.headers.Location
import org.specs2.specification.Tables

class RetrySpec extends Http4sSpec with Tables {

  val route = HttpService {
    case _ -> Root / status =>
      Task.now(Response(Status.fromInt(status.toInt).valueOr(throw _)))
    case _ -> Root / status =>
      Task.now(Response(BadRequest))
  }

  val defaultClient = Client.fromHttpService(route)

  "default policy" should {
    def countRetries(client: Client, method: Method, status: Status, body: EntityBody): Int = {
      val max = 2
      var attemptsCounter = 1
      val policy = RetryPolicy { attempts: Int =>
        if (attempts >= max) None
        else {
          attemptsCounter = attemptsCounter + 1
          Some(10.milliseconds)
        }
      }
      val retryClient = Retry(policy)(client)
      val req = Request(method, uri("http://localhost/") / status.code.toString).withBody(body)
      val resp = retryClient.fetch(req){ _ => Task.now(()) }.unsafePerformSyncAttempt
      attemptsCounter
    }

    "retry GET based on status code" in {
      "status"                | "retries" |>
      Ok                      ! 1         |
      Found                   ! 1         |
      BadRequest              ! 1         |
      NotFound                ! 1         |
      RequestTimeout          ! 2         |
      InternalServerError     ! 2         |
      NotImplemented          ! 1         |
      BadGateway              ! 2         |
      ServiceUnavailable      ! 2         |
      GatewayTimeout          ! 2         |
      HttpVersionNotSupported ! 1         |
      { countRetries(defaultClient, GET, _, EmptyBody) must_== _ }
    }

    "not retry POSTs" in prop { s: Status =>
      countRetries(defaultClient, POST, s, EmptyBody) must_== 1
    }

    "not retry effectful bodies" in prop { s: Status =>
      countRetries(defaultClient, PUT, s, Process.eval_(Task.now(()))) must_== 1
    }

    "retry exceptions" in {
      val failClient = Client(Service.const(Task.fail(new Exception("boom"))), Task.now(()))
      countRetries(failClient, GET, InternalServerError, EmptyBody) must_== 2
    }
  }
}
