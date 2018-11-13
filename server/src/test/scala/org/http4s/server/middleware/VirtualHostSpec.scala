package org.http4s
package server
package middleware

import cats.effect._
import cats.syntax.applicative._
import org.http4s.Method._
import org.http4s.Status.{BadRequest, NotFound, Ok}
import org.http4s.Uri.uri
import org.http4s.headers.Host

class VirtualHostSpec extends Http4sSpec {

  val default = HttpRoutes.of[IO] {
    case _ => Response[IO](Ok).withEntity("default").pure[IO]
  }

  val routesA = HttpRoutes.of[IO] {
    case _ => Response[IO](Ok).withEntity("routesA").pure[IO]
  }

  val routesB = HttpRoutes.of[IO] {
    case _ => Response[IO](Ok).withEntity("routesB").pure[IO]
  }

  "VirtualHost" >> {

    val vhost = VirtualHost(
      VirtualHost.exact(default, "default", None),
      VirtualHost.exact(routesA, "routesA", None),
      VirtualHost.exact(routesB, "routesB", Some(80))
    ).orNotFound

    "exact" should {
      "return a 400 BadRequest when no header is present on a NON HTTP/1.0 request" in {
        val req1 = Request[IO](GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/1.1`)
        val req2 = Request[IO](GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/2.0`)

        vhost(req1) must returnStatus(BadRequest)
        vhost(req2) must returnStatus(BadRequest)
      }

      "honor the Host header host" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .withHeaders(Host("routesA"))

        vhost(req) must returnBody("routesA")
      }

      "honor the Host header port" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .withHeaders(Host("routesB", Some(80)))

        vhost(req) must returnBody("routesB")
      }

      "ignore the Host header port if not specified" in {
        val good = Request[IO](GET, uri("/numbers/1"))
          .withHeaders(Host("routesA", Some(80)))

        vhost(good) must returnBody("routesA")
      }

      "result in a 404 if the hosts fail to match" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .withHeaders(Host("routesB", Some(8000)))

        vhost(req) must returnStatus(NotFound)
      }
    }

    "wildcard" should {
      val vhost = VirtualHost(
        VirtualHost.wildcard(routesA, "routesa", None),
        VirtualHost.wildcard(routesB, "*.service", Some(80)),
        VirtualHost.wildcard(default, "*.foo-service", Some(80))
      ).orNotFound

      "match an exact route" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .withHeaders(Host("routesa", Some(80)))

        vhost(req) must returnBody("routesA")
      }

      "allow for a dash in the service" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .withHeaders(Host("foo.foo-service", Some(80)))

        vhost(req) must returnBody("default")
      }

      "match a route with a wildcard route" in {
        val req = Request[IO](GET, uri("/numbers/1"))
        val reqs = Seq(
          req.withHeaders(Host("a.service", Some(80))),
          req.withHeaders(Host("A.service", Some(80))),
          req.withHeaders(Host("b.service", Some(80))))

        forall(reqs) { req =>
          vhost(req) must returnBody("routesB")
        }
      }

      "not match a route with an abscent wildcard" in {
        val req = Request[IO](GET, uri("/numbers/1"))
        val reqs = Seq(
          req.withHeaders(Host(".service", Some(80))),
          req.withHeaders(Host("service", Some(80))))

        forall(reqs) { req =>
          vhost(req) must returnStatus(NotFound)
        }
      }
    }
  }

}
