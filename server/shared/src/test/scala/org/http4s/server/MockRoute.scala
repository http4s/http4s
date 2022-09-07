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

import cats.effect._
import cats.syntax.all._
import org.http4s.Status.Accepted
import org.http4s.Status.Ok
import org.http4s.syntax.literals._

object MockRoute {
  def route(): HttpRoutes[IO] =
    HttpRoutes.of {
      case req if req.uri.path === path"/ping" =>
        Response[IO](Ok).withEntity("pong").pure[IO]

      case req if req.method === Method.POST && req.uri.path === path"/echo" =>
        IO.pure(Response[IO](body = req.body))

      case req if req.uri.path === path"/withslash" =>
        IO.pure(Response(Ok))

      case req if req.uri.path === path"/withslash/" =>
        IO.pure(Response(Accepted))

      case req if req.uri.path === path"/fail" =>
        sys.error("Problem!")
    }
}
