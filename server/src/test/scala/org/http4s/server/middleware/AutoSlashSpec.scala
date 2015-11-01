package org.http4s.server.middleware

import org.http4s.{Status, Request, Http4sSpec}
import org.http4s.server.MockRoute


class AutoSlashSpec extends Http4sSpec {

  val route = MockRoute.route()

  "AutoSlash" should {
    "Auto remove a trailing slash" in {
      val req = Request(uri = uri("/ping/"))
      route.apply(req).run.status must_== (Status.NotFound)
      AutoSlash(route).apply(req).run.status must_== (Status.Ok)
    }

    "Match a route defined with a slash" in {
      AutoSlash(route).apply(Request(uri = uri("/withslash"))).run.status must_== (Status.Ok)
      AutoSlash(route).apply(Request(uri = uri("/withslash/"))).run.status must_== (Status.Accepted)
    }

    "Respect an absent trailing slash" in {
      val req = Request(uri = uri("/ping"))
      route.apply(req).run.status must_== (Status.Ok)
      AutoSlash(route).apply(req).run.status must_== (Status.Ok)
    }

    "Not crash on empy path" in {
      val req = Request(uri = uri(""))
      AutoSlash(route).apply(req).run.status must_== (Status.NotFound)
    }
  }
}
