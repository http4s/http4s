/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.implicits._
import cats.effect._
import org.http4s.Method._
import org.http4s.Status.{BadRequest, NotFound, Ok}
import org.http4s.syntax.all._
import org.http4s.Uri.uri
import org.http4s.headers.Host

class VirtualHostSuite extends Http4sSuite {
  val default = HttpRoutes.of[IO] { case _ =>
    Response[IO](Ok).withEntity("default").pure[IO]
  }

  val routesA = HttpRoutes.of[IO] { case _ =>
    Response[IO](Ok).withEntity("routesA").pure[IO]
  }

  val routesB = HttpRoutes.of[IO] { case _ =>
    Response[IO](Ok).withEntity("routesB").pure[IO]
  }

  val vhostExact = VirtualHost(
    VirtualHost.exact(default, "default", None),
    VirtualHost.exact(routesA, "routesA", None),
    VirtualHost.exact(routesB, "routesB", Some(80))
  ).orNotFound

  test("exact should return a 400 BadRequest when no header is present on a NON HTTP/1.0 request") {
    val req1 = Request[IO](GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/1.1`)
    val req2 = Request[IO](GET, uri("/numbers/1"), httpVersion = HttpVersion.`HTTP/2.0`)

    vhostExact(req1).map(_.status).assertEquals(BadRequest) *>
      vhostExact(req2).map(_.status).assertEquals(BadRequest)
  }

  test("exact should honor the Host header host") {
    val req = Request[IO](GET, uri("/numbers/1"))
      .withHeaders(Host("routesA"))

    vhostExact(req).flatMap(_.as[String]).assertEquals("routesA")
  }

  test("exact should honor the Host header port") {
    val req = Request[IO](GET, uri("/numbers/1"))
      .withHeaders(Host("routesB", Some(80)))

    vhostExact(req).flatMap(_.as[String]).assertEquals("routesB")
  }

  test("exact should ignore the Host header port if not specified") {
    val good = Request[IO](GET, uri("/numbers/1"))
      .withHeaders(Host("routesA", Some(80)))

    vhostExact(good).flatMap(_.as[String]).assertEquals("routesA")
  }

  test("exact should result in a 404 if the hosts fail to match") {
    val req = Request[IO](GET, uri("/numbers/1"))
      .withHeaders(Host("routesB", Some(8000)))

    vhostExact(req).map(_.status).assertEquals(NotFound)
  }

  val vhostWildcard = VirtualHost(
    VirtualHost.wildcard(routesA, "routesa", None),
    VirtualHost.wildcard(routesB, "*.service", Some(80)),
    VirtualHost.wildcard(default, "*.foo-service", Some(80))
  ).orNotFound

  test("wildcard match an exact route") {
    val req = Request[IO](GET, uri("/numbers/1"))
      .withHeaders(Host("routesa", Some(80)))

    vhostWildcard(req).flatMap(_.as[String]).assertEquals("routesA")
  }

  test("wildcard allow for a dash in the service") {
    val req = Request[IO](GET, uri("/numbers/1"))
      .withHeaders(Host("foo.foo-service", Some(80)))

    vhostWildcard(req).flatMap(_.as[String]).assertEquals("default")
  }

  test("wildcard match a route with a wildcard route") {
    val req = Request[IO](GET, uri("/numbers/1"))
    val reqs = List(
      req.withHeaders(Host("a.service", Some(80))),
      req.withHeaders(Host("A.service", Some(80))),
      req.withHeaders(Host("b.service", Some(80))))

    reqs.traverse { req =>
      vhostWildcard(req).flatMap(_.as[String]).assertEquals("routesB")
    }
  }

  test("wildcard not match a route with an abscent wildcard") {
    val req = Request[IO](GET, uri("/numbers/1"))
    val reqs =
      List(req.withHeaders(Host(".service", Some(80))), req.withHeaders(Host("service", Some(80))))

    reqs.traverse { req =>
      vhostWildcard(req).map(_.status).assertEquals(NotFound)
    }
  }
}
