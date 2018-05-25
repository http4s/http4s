package org.http4s
package client

import cats.effect._
import java.io.IOException
import org.http4s.Method._
import org.http4s.Status.Ok
import org.http4s.headers.Host
import org.http4s.server.middleware.VirtualHost
import org.http4s.server.middleware.VirtualHost.exact

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

    "include a Host header in requests whose URIs are absolute" in {
      val hostClient = Client.fromHttpService(HttpService[IO] {
        case r => Response[IO](Ok).withBody(r.headers.get(Host).map(_.value).getOrElse("None"))
      })

      hostClient
        .expect[String](Request[IO](GET, Uri.uri("https://http4s.org/")))
        .unsafeRunSync() must_== "http4s.org"
    }

    "include a Host header with a port when the port is non-standard" in {
      val hostClient = Client.fromHttpService(HttpService[IO] {
        case r => Response[IO](Ok).withBody(r.headers.get(Host).map(_.value).getOrElse("None"))
      })

      hostClient
        .expect[String](Request[IO](GET, Uri.uri("https://http4s.org:1983/")))
        .unsafeRunSync() must_== "http4s.org:1983"
    }

    "cooperate with the VirtualHost server middleware" in {
      val service = HttpService[IO] {
        case r => Response[IO](Ok).withBody(r.headers.get(Host).map(_.value).getOrElse("None"))
      }

      val hostClient = Client.fromHttpService(VirtualHost(exact(service, "http4s.org")))

      hostClient
        .expect[String](Request[IO](GET, Uri.uri("https://http4s.org/")))
        .unsafeRunSync() must_== "http4s.org"
    }
  }
}
