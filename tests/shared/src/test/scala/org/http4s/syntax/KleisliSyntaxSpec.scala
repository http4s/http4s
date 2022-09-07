/*
 * Copyright 2013 http4s.org
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

import cats.data.Kleisli
import cats.effect.IO

class KleisliSyntaxTests {
  // We're not testing any runtime behaviour here, just that the syntax compiles without imports.

  // translate syntax should work on HttpRoutes
  val routes: HttpRoutes[Kleisli[IO, Int, *]] = HttpRoutes
    .of[IO] { case _ =>
      IO(Response[IO](Status.Ok))
    }
    .translate[Kleisli[IO, Int, *]](Kleisli.liftK)(Kleisli.applyK(42))

  // translate syntax should work on HttpApp
  val app: HttpApp[Kleisli[IO, Int, *]] = HttpApp[IO] { case _ =>
    IO(Response[IO](Status.Ok))
  }
    .translate[Kleisli[IO, Int, *]](Kleisli.liftK)(Kleisli.applyK(42))

  // translate syntax should work on AuthedRoutes
  val authedRoutes: AuthedRoutes[String, Kleisli[IO, Int, *]] = AuthedRoutes
    .of[String, IO] { case _ =>
      IO(Response[IO](Status.Ok))
    }
    .translate[Kleisli[IO, Int, *]](Kleisli.liftK)(Kleisli.applyK(42))
}
