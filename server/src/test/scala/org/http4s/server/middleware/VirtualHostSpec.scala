package org.http4s
package server
package middleware

import cats.effect._
import cats.syntax.applicative._
import org.http4s.Method._
import org.http4s.headers.Host
import org.http4s.Status.{BadRequest, NotFound, Ok}

class VirtualHostSpec extends Http4sSpec {

  val default = HttpService[IO] {
    case _ => Response[IO](Ok).withEntity("default").pure[IO]
  }

  val servicea = HttpService[IO] {
    case _ => Response[IO](Ok).withEntity("servicea").pure[IO]
  }

  val serviceb = HttpService[IO] {
    case _ => Response[IO](Ok).withEntity("serviceb").pure[IO]
  }

  "VirtualHost" >> {

    val virtualServices = VirtualHost(
      VirtualHost.exact(default, "default", None),
      VirtualHost.exact(servicea, "servicea", None),
      VirtualHost.exact(serviceb, "serviceb", Some(80))
    )

    "exact" should {
      "return a 400 BadRequest when no header is present on a NON HTTP/1.0 request" in {
        val req1 = Request[IO](GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/1.1`)
        val req2 = Request[IO](GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/2.0`)

        virtualServices.orNotFound(req1) must returnStatus(BadRequest)
        virtualServices.orNotFound(req2) must returnStatus(BadRequest)
      }

      "honor the Host header host" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("servicea"))

        virtualServices.orNotFound(req) must returnBody("servicea")
      }

      "honor the Host header port" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("serviceb", Some(80)))

        virtualServices.orNotFound(req) must returnBody("serviceb")
      }

      "ignore the Host header port if not specified" in {
        val good = Request[IO](GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("servicea", Some(80)))

        virtualServices.orNotFound(good) must returnBody("servicea")
      }

      "result in a 404 if the hosts fail to match" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("serviceb", Some(8000)))

        virtualServices.orNotFound(req) must returnStatus(NotFound)
      }
    }

    "wildcard" should {
      val virtualServices = VirtualHost(
        VirtualHost.wildcard(servicea, "servicea", None),
        VirtualHost.wildcard(serviceb, "*.service", Some(80)),
        VirtualHost.wildcard(default, "*.foo-service", Some(80))
      )

      "match an exact route" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("servicea", Some(80)))

        virtualServices.orNotFound(req) must returnBody("servicea")
      }

      "allow for a dash in the service" in {
        val req = Request[IO](GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("foo.foo-service", Some(80)))

        virtualServices.orNotFound(req) must returnBody("default")
      }

      "match a route with a wildcard route" in {
        val req = Request[IO](GET, uri("/numbers/1"))
        val reqs = Seq(
          req.replaceAllHeaders(Host("a.service", Some(80))),
          req.replaceAllHeaders(Host("A.service", Some(80))),
          req.replaceAllHeaders(Host("b.service", Some(80))))

        forall(reqs) { req =>
          virtualServices.orNotFound(req) must returnBody("serviceb")
        }
      }

      "not match a route with an abscent wildcard" in {
        val req = Request[IO](GET, uri("/numbers/1"))
        val reqs = Seq(
          req.replaceAllHeaders(Host(".service", Some(80))),
          req.replaceAllHeaders(Host("service", Some(80))))

        forall(reqs) { req =>
          virtualServices.orNotFound(req) must returnStatus(NotFound)
        }
      }
    }
  }

}
