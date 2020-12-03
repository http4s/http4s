/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.effect._
import cats.syntax.all._
import org.http4s.Status.{Accepted, Ok}
import org.http4s.server.middleware.PushSupport._

object MockRoute {
  def route(): HttpRoutes[IO] =
    HttpRoutes.of {
      case req if req.uri.path === "/ping" =>
        Response[IO](Ok).withEntity("pong").pure[IO]

      case req if req.method === Method.POST && req.uri.path === "/echo" =>
        IO.pure(Response[IO](body = req.body))

      case req if req.uri.path === "/withslash" =>
        IO.pure(Response(Ok))

      case req if req.uri.path === "/withslash/" =>
        IO.pure(Response(Accepted))

      case req if req.uri.path === "/fail" =>
        sys.error("Problem!")

      /** For testing the PushSupport middleware */
      case req if req.uri.path === "/push" =>
        Response[IO](Ok).withEntity("Hello").push("/ping")(req).pure[IO]
    }
}
