package org.http4s.server.middleware

import org.http4s.{Status, Request, Http4sSpec}
import org.http4s.server.MockRoute


class AutoSlashSpec extends Http4sSpec {

  val route = MockRoute.route()

  "AutoSlash" should {
    "Auto remove a trailing slash" in {
      val req = Request(uri = uri("/ping/"))
      route(req).run.status must equal (Status.NotFound)
      AutoSlash(route)(req).run.status must equal (Status.Ok)
    }

    "Match a route defined with a slash" in {
      AutoSlash(route)(Request(uri = uri("/withslash"))).run.status must equal (Status.Ok)
      AutoSlash(route)(Request(uri = uri("/withslash/"))).run.status must equal (Status.Accepted)
    }

    "Respect an absent trailing slash" in {
      val req = Request(uri = uri("/ping"))
      route(req).run.status must equal (Status.Ok)
      AutoSlash(route)(req).run.status must equal (Status.Ok)
    }

    "Not crash on empy path" in {
      val req = Request(uri = uri(""))
      AutoSlash(route)(req).run.status must equal (Status.NotFound)
    }
  }
}
