package org.http4s
package server
package middleware

class AutoSlashSpec extends Http4sSpec {

  val route = MockRoute.route()

  "AutoSlash" should {
    "Auto remove a trailing slash" in {
      val req = Request(uri = uri("/ping/"))
      route.apply(req) must runToStatus(Status.NotFound)
      AutoSlash(route).apply(req) must runToStatus(Status.Ok)
    }

    "Match a route defined with a slash" in {
      AutoSlash(route).apply(Request(uri = uri("/withslash"))) must runToStatus(Status.Ok)
      AutoSlash(route).apply(Request(uri = uri("/withslash/"))) must runToStatus(Status.Accepted)
    }

    "Respect an absent trailing slash" in {
      val req = Request(uri = uri("/ping"))
      route.apply(req) must runToStatus(Status.Ok)
      AutoSlash(route).apply(req) must runToStatus(Status.Ok)
    }

    "Not crash on empy path" in {
      val req = Request(uri = uri(""))
      AutoSlash(route).apply(req) must runToStatus(Status.NotFound)
    }
  }
}
