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
package multipart

import cats.data.EitherT
import cats.effect._
import org.http4s.EntityEncoder._
import org.http4s.headers._

import java.io.File
import scala.annotation.nowarn

trait MultipartSuitePlatform { self: MultipartSuite =>

  def multipartSpecPlatform(name: String)(
      mkDecoder: Resource[IO, EntityDecoder[IO, Multipart[IO]]]
  ) = {
          test(s"Multipart form data $name should encoded and decoded with binary data") {
        val file = new File(getClass.getResource("/ball.png").toURI)

        val field1 = Part.formData[IO]("field1", "Text_Field_1")
        val field2 = Part
          .fileData[IO]("image", file, `Content-Type`(MediaType.image.png))

        val multipart = Multipart[IO](Vector(field1, field2))

        val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
        val body = entity.body
        val request =
          Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

        mkDecoder.use { decoder =>
          val decoded = decoder.decode(request, true)
          val result = decoded.value

          assertIOBoolean(EitherT(result).semiflatMap(eqMultipartIO(_, multipart)).getOrElse(false))
        }
      }

  }

  {
    @nowarn("cat=deprecation")
    val testDeprecated =
      multipartSpec("with mixed decoder")(Resource.pure(EntityDecoder.mixedMultipart[IO]()))
    testDeprecated
  }
  multipartSpec("with mixed resource decoder")(EntityDecoder.mixedMultipartResource[IO]())

}
