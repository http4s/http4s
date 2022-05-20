/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import org.http4s.Method._
import org.http4s.Status.BadRequest
import org.http4s.Status.NotFound
import org.http4s.Status.Ok
import org.http4s.headers.Host
import org.http4s.syntax.all._

class VirtualHostSuite extends Http4sSuite {
  private val default = HttpRoutes.of[IO] { case _ =>
    Response[IO](Ok).withEntity("default").pure[IO]
  }

  private val routesA = HttpRoutes.of[IO] { case _ =>
    Response[IO](Ok).withEntity("routesA").pure[IO]
  }

  private val routesB = HttpRoutes.of[IO] { case _ =>
    Response[IO](Ok).withEntity("routesB").pure[IO]
  }

  private val vhostExact = VirtualHost(
    VirtualHost.exact(default, "default", None),
    VirtualHost.exact(routesA, "routesA", None),
    VirtualHost.exact(routesB, "routesB", Some(80)),
  ).orNotFound

  test("exact should return a 400 BadRequest when no header is present on a NON HTTP/1.0 request") {
    val req1 = Request[IO](GET, uri"/numbers/1", httpVersion = HttpVersion.`HTTP/1.1`)
    val req2 = Request[IO](GET, uri"/numbers/1", httpVersion = HttpVersion.`HTTP/2`)

    vhostExact(req1).map(_.status).assertEquals(BadRequest) *>
      vhostExact(req2).map(_.status).assertEquals(BadRequest)
  }

  test("exact should honor the Host header host") {
    val req = Request[IO](GET, uri"/numbers/1")
      .withHeaders(Host("routesA"))

    vhostExact(req).flatMap(_.as[String]).assertEquals("routesA")
  }

  test("exact should honor the Host header port") {
    val req = Request[IO](GET, uri"/numbers/1")
      .withHeaders(Host("routesB", Some(80)))

    vhostExact(req).flatMap(_.as[String]).assertEquals("routesB")
  }

  test("exact should ignore the Host header port if not specified") {
    val good = Request[IO](GET, uri"/numbers/1")
      .withHeaders(Host("routesA", Some(80)))

    vhostExact(good).flatMap(_.as[String]).assertEquals("routesA")
  }

  test("exact should result in a 404 if the hosts fail to match") {
    val req = Request[IO](GET, uri"/numbers/1")
      .withHeaders(Host("routesB", Some(8000)))

    vhostExact(req).map(_.status).assertEquals(NotFound)
  }

  private val vhostWildcard = VirtualHost(
    VirtualHost.wildcard(routesA, "routesa", None),
    VirtualHost.wildcard(routesB, "*.service", Some(80)),
    VirtualHost.wildcard(default, "*.foo-service", Some(80)),
  ).orNotFound

  test("wildcard match an exact route") {
    val req = Request[IO](GET, uri"/numbers/1")
      .withHeaders(Host("routesa", Some(80)))

    vhostWildcard(req).flatMap(_.as[String]).assertEquals("routesA")
  }

  test("wildcard allow for a dash in the service") {
    val req = Request[IO](GET, uri"/numbers/1")
      .withHeaders(Host("foo.foo-service", Some(80)))

    vhostWildcard(req).flatMap(_.as[String]).assertEquals("default")
  }

  test("wildcard match a route with a wildcard route") {
    val req = Request[IO](GET, uri"/numbers/1")
    val reqs = List(
      req.withHeaders(Host("a.service", Some(80))),
      req.withHeaders(Host("A.service", Some(80))),
      req.withHeaders(Host("b.service", Some(80))),
    )

    reqs.parTraverse_ { req =>
      vhostWildcard(req).flatMap(_.as[String]).assertEquals("routesB")
    }
  }

  test("wildcard not match a route with an abscent wildcard") {
    val req = Request[IO](GET, uri"/numbers/1")
    val reqs =
      List(req.withHeaders(Host(".service", Some(80))), req.withHeaders(Host("service", Some(80))))

    reqs.parTraverse_ { req =>
      vhostWildcard(req).map(_.status).assertEquals(NotFound)
    }
  }
}
