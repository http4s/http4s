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
import cats.syntax.all._
import com.example.http4s.blaze.demo.server.service.FileService
import org.http4s.EntityDecoder.multipart
import org.http4s.{ApiVersion => _, _}
import org.http4s.dsl.Http4sDsl
import org.http4s.multipart.Part

class MultipartHttpEndpoint[F[_]](fileService: FileService[F])(implicit F: Sync[F])
    extends Http4sDsl[F] {
  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "multipart" =>
      Ok("Send a file (image, sound, etc) via POST Method")

    case req @ POST -> Root / ApiVersion / "multipart" =>
      req.decodeWith(multipart[F], strict = true) { response =>
        def filterFileTypes(part: Part[F]): Boolean =
          part.headers.toList.exists(_.value.contains("filename"))

        val stream = response.parts.filter(filterFileTypes).traverse(fileService.store)

        Ok(stream.map(_ => s"Multipart file parsed successfully > ${response.parts}"))
      }
  }
}
