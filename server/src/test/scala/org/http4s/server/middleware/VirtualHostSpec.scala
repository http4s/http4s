package org.http4s
package server
package middleware

import org.http4s.Method._
import org.http4s.headers.Host
import org.http4s.Status.{Ok,BadRequest,NotFound}


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

        virtualServices.apply(req1).run.status must_== BadRequest
        virtualServices.apply(req2).run.status must_== BadRequest
      }

      "honor the Host header host" in {
        val req = Request(GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("servicea"))

        virtualServices.apply(req).run.as[String].run must_== ("servicea")
      }

      "honor the Host header port" in {
        val req = Request(GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("serviceb", Some(80)))

        virtualServices.apply(req).run.as[String].run must_== ("serviceb")
      }

      "ignore the Host header port if not specified" in {
        val good = Request(GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("servicea", Some(80)))

        virtualServices.apply(good).run.as[String].run must_== ("servicea")
      }

      "result in a 404 if the hosts fail to match" in {
        val req = Request(GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("serviceb", Some(8000)))

        virtualServices.apply(req).run.status must_== NotFound
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
          .replaceAllHeaders(Host("servicea", Some(80)))

        virtualServices.apply(req).run.as[String].run must_== ("servicea")
      }

      "allow for a dash in the service" in {
        val req = Request(GET, uri("/numbers/1"))
          .replaceAllHeaders(Host("foo.foo-service", Some(80)))

        virtualServices.apply(req).run.as[String].run must_== ("default")
      }

      "match a route with a wildcard route" in {
        val req = Request(GET, uri("/numbers/1"))
        val reqs = Seq(req.replaceAllHeaders(Host("a.service", Some(80))),
                       req.replaceAllHeaders(Host("A.service", Some(80))),
                       req.replaceAllHeaders(Host("b.service", Some(80))))

        forall(reqs){ req =>
          virtualServices.apply(req).run.as[String].run must_== ("serviceb")
        }
      }

      "not match a route with an abscent wildcard" in {
        val req = Request(GET, uri("/numbers/1"))
        val reqs = Seq(req.replaceAllHeaders(Host(".service", Some(80))),
                       req.replaceAllHeaders(Host("service", Some(80))))

        forall(reqs){ req =>
          virtualServices.apply(req).run.status must_== NotFound
        }
      }
    }
  }

}
