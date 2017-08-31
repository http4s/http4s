package org.http4s
package client
package middleware

import cats.effect.IO
import cats.implicits._
import fs2._
import org.http4s.dsl.io._
import org.specs2.specification.Tables

import scala.concurrent.duration._

class RetrySpec extends Http4sSpec with Tables {

  val route = HttpService[IO] {
    case _ -> Root / status =>
      IO.pure(Response(Status.fromInt(status.toInt).valueOr(throw _)))
    case _ -> Root / status =>
      BadRequest()
  }

  val defaultClient: Client[IO] = Client.fromHttpService(route)

  def countRetries(
      client: Client[IO],
      method: Method,
      status: Status,
      body: EntityBody[IO]): Int = {
    val max = 2
    var attemptsCounter = 1
    val policy = RetryPolicy[IO] { attempts: Int =>
      if (attempts >= max) None
      else {
        attemptsCounter = attemptsCounter + 1
        Some(10.milliseconds)
      }
    }
    val retryClient = Retry[IO](policy)(client)
    val req = Request[IO](method, uri("http://localhost/") / status.code.toString).withBody(body)
    val resp = retryClient
      .fetch(req) { _ =>
        IO.unit
      }
      .attempt
      .unsafeRunSync()
    attemptsCounter
  }

  "defaultRetriable" should {
    "retry GET based on status code" in {
      "status" | "retries" |>
        Ok ! 1 |
        Found ! 1 |
        BadRequest ! 1 |
        NotFound ! 1 |
        RequestTimeout ! 2 |
        InternalServerError ! 2 |
        NotImplemented ! 1 |
        BadGateway ! 2 |
        ServiceUnavailable ! 2 |
        GatewayTimeout ! 2 |
        HttpVersionNotSupported ! 1 | { countRetries(defaultClient, GET, _, EmptyBody) must_== _ }
    }

    "not retry non-idempotent methods" in prop { s: Status =>
      countRetries(defaultClient, POST, s, EmptyBody) must_== 1
    }

    "not retry effectful bodies" in prop { s: Status =>
      countRetries(defaultClient, PUT, s, Stream.eval_(IO.unit)) must_== 1
    }

    "retry exceptions" in {
      val failClient = Client[IO](Service.const(IO.raiseError(new Exception("boom"))), IO.unit)
      countRetries(failClient, GET, InternalServerError, EmptyBody) must_== 2
    }
  }
}
