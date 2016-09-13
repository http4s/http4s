package org.http4s.server.middleware

import org.http4s.{Status, Request, Http4sSpec}
import org.http4s.server.MockRoute


class AutoSlashSpec extends Http4sSpec {

  val route = MockRoute.route()

  "AutoSlash" should {
    "Auto remove a trailing slash" in {
      val req = Request(uri = uri("/ping/"))
      route.apply(req) must returnStatus(Status.NotFound)
      AutoSlash(route).apply(req) must returnStatus(Status.Ok)
    }

    "Match a route defined with a slash" in {
      AutoSlash(route).apply(Request(uri = uri("/withslash"))) must returnStatus(Status.Ok)
      AutoSlash(route).apply(Request(uri = uri("/withslash/"))) must returnStatus(Status.Accepted)
    }

    "Respect an absent trailing slash" in {
      val req = Request(uri = uri("/ping"))
      route.apply(req) must returnStatus(Status.Ok)
      AutoSlash(route).apply(req) must returnStatus(Status.Ok)
    }

    "Not crash on empy path" in {
      val req = Request(uri = uri(""))
      AutoSlash(route).apply(req) must returnStatus(Status.NotFound)
    }
  }
}
