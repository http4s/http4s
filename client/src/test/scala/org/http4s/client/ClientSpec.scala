package org.http4s
package client

import cats.effect._
import java.io.IOException
import org.http4s.Method._
import org.http4s.Status.Ok

class ClientSpec extends Http4sSpec {
  val service = HttpService[IO] {
    case r => Response[IO](Ok).withBody(r.body)
  }
  val client: Client[IO] = Client.fromHttpService(service)

  "mock client" should {
    "read body before dispose" in {
      client.expect[String](Request[IO](POST).withBody("foo")).unsafeRunSync() must_== "foo"
    }

    "fail to read body after dispose" in {
      Request[IO](POST)
        .withBody("foo")
        .flatMap { req =>
          // This is bad.  Don't do this.
          client.fetch(req)(IO.pure).flatMap(_.as[String])
        }
        .attempt
        .unsafeRunSync() must beLeft.like {
        case e: IOException => e.getMessage == "response was disposed"
      }
    }

    "fail to read body after client shutdown" in {
      val client: Client[IO] = Client.fromHttpService(service)
      client.shutdownNow()
      client.expect[String](Request[IO](POST).withBody("foo")).attempt.unsafeRunSync() must beLeft
        .like {
          case e: IOException => e.getMessage == "client was shut down"
        }
    }
  }
}
