package org.http4s
package server.middleware

import org.http4s.Method._
import org.http4s.headers.Host
import org.http4s.Status.{Ok,BadRequest,NotFound}
import org.http4s.server.HttpService


class VirtualHostSpec extends Http4sSpec {

  val default = HttpService {
    case req => Response(Ok).withBody("default")
  }

  val servicea = HttpService {
    case req => Response(Ok).withBody("servicea")
  }

  val serviceb = HttpService {
    case req => Response(Ok).withBody("serviceb")
  }



  "VirtualHost" >> {

    val virtualServices = VirtualHost(
      VirtualHost.exact(default, "default", None),
      VirtualHost.exact(servicea, "servicea", None),
      VirtualHost.exact(serviceb, "serviceb", Some(80))
    )

    "exact" should {
      "return a 400 BadRequest when no header is present on a NON HTTP/1.0 request" in {
        val req1 = Request(GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/1.1`)
        val req2 = Request(GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/2.0`)

        virtualServices(req1).run.status must_== BadRequest
        virtualServices(req2).run.status must_== BadRequest
      }

      "honor the Host header host" in {
        val req = Request(GET, uri("/numbers/1"))
          .withHeaders(Host("servicea"))

        virtualServices(req).run.as[String].run must equal ("servicea")
      }

      "honor the Host header port" in {
        val req = Request(GET, uri("/numbers/1"))
          .withHeaders(Host("serviceb", Some(80)))

        virtualServices(req).run.as[String].run must equal ("serviceb")
      }

      "ignore the Host header port if not specified" in {
        val good = Request(GET, uri("/numbers/1"))
          .withHeaders(Host("servicea", Some(80)))

        virtualServices(good).run.as[String].run must equal ("servicea")
      }

      "result in a 404 if the hosts fail to match" in {
        val req = Request(GET, uri("/numbers/1"))
          .withHeaders(Host("serviceb", Some(8000)))

        virtualServices(req).run.status must_== NotFound
      }
    }

    "wildcard" should {
      val virtualServices = VirtualHost(
        VirtualHost.wildcard(servicea, "servicea", None),
        VirtualHost.wildcard(serviceb, "*.service", Some(80)),
        VirtualHost.wildcard(default, "*.foo-service", Some(80))
      )

      "match an exact route" in {
        val req = Request(GET, uri("/numbers/1"))
          .withHeaders(Host("servicea", Some(80)))

        virtualServices(req).run.as[String].run must equal ("servicea")
      }

      "allow for a dash in the service" in {
        val req = Request(GET, uri("/numbers/1"))
          .withHeaders(Host("foo.foo-service", Some(80)))

        virtualServices(req).run.as[String].run must equal ("default")
      }

      "match a route with a wildcard route" in {
        val req = Request(GET, uri("/numbers/1"))
        val reqs = Seq(req.withHeaders(Host("a.service", Some(80))),
                       req.withHeaders(Host("A.service", Some(80))),
                       req.withHeaders(Host("b.service", Some(80))))

        forall(reqs){ req =>
          virtualServices(req).run.as[String].run must equal ("serviceb")
        }
      }

      "not match a route with an abscent wildcard" in {
        val req = Request(GET, uri("/numbers/1"))
        val reqs = Seq(req.withHeaders(Host(".service", Some(80))),
                       req.withHeaders(Host("service", Some(80))))

        forall(reqs){ req =>
          virtualServices(req).run.status must_== NotFound
        }
      }
    }
  }

}
