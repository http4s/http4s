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
import com.example.http4s.blaze.demo.server.service.FileService
import org.http4s._
import org.http4s.dsl.Http4sDsl

class FileHttpEndpoint[F[_]: Sync](fileService: FileService[F]) extends Http4sDsl[F] {
  object DepthQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("depth")

  val service: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "dirs" :? DepthQueryParamMatcher(depth) =>
      Ok(fileService.homeDirectories(depth))
  }
}
