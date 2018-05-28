package org.http4s
package client

import cats.effect._
import cats.implicits._
import java.io.IOException
import org.http4s.Method._
import org.http4s.Status.Ok

class ClientSpec extends Http4sSpec {
  val app = HttpApp[IO] {
    case r => Response[IO](Ok).withEntity(r.body).pure[IO]
  }
  val client: Client[IO] = Client.fromHttpApp(app)

  "mock client" should {
    "read body before dispose" in {
      client.expect[String](Request[IO](POST).withEntity("foo")).unsafeRunSync() must_== "foo"
    }

    "fail to read body after dispose" in {
      Request[IO](POST)
        .withEntity("foo")
        .pure[IO]
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
      val client: Client[IO] = Client.fromHttpApp(app)
      client.shutdownNow()
      client.expect[String](Request[IO](POST).withEntity("foo")).attempt.unsafeRunSync() must beLeft
        .like {
          case e: IOException => e.getMessage == "client was shut down"
        }
    }
  }
}
