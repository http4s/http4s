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

package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.Sync
import org.http4s.{ApiVersion => _, _}
import org.http4s.dsl.Http4sDsl

class HexNameHttpEndpoint[F[_]: Sync] extends Http4sDsl[F] {
  object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "hex" :? NameQueryParamMatcher(name) =>
      Ok(name.getBytes("UTF-8").map("%02x".format(_)).mkString)
  }
}
