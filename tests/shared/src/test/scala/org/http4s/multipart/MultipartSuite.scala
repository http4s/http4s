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
import cats.effect.std.Random
import cats.syntax.all._
import fs2._
import org.http4s.EntityEncoder._
import org.http4s.headers._
import org.http4s.syntax.literals._
import org.typelevel.ci._

import scala.annotation.nowarn

class MultipartSuite extends Http4sSuite {
  private val url = uri"https://example.com/path/to/some/where"

  private val multiparts =
    Random
      .scalaUtilRandom[IO]
      .map(Multiparts.fromRandom[IO])
      .syncStep(Int.MaxValue)
      .unsafeRunSync()
      .toOption
      .get

  def eqPartIO(a: Part[IO], b: Part[IO]): IO[Boolean] =
    for {
      abv <- a.body.compile.toVector
      bbv <- b.body.compile.toVector
    } yield a.headers === b.headers && abv === bbv

  def eqMultipartIO(a: Multipart[IO], b: Multipart[IO]): IO[Boolean] =
    a.parts
      .zip(b.parts)
      .traverse { case (a, b) =>
        eqPartIO(a, b)
      }
      .map { parts =>
        a.headers === b.headers &&
        a.boundary === b.boundary &&
        parts.forall(identity)
      }

  private def multipartSpec(name: String)(
      mkDecoder: Resource[IO, EntityDecoder[IO, Multipart[IO]]]
  ) = {
    test(s"Multipart form data $name should be encoded and decoded with content types") {
      val field1 =
        Part.formData[IO]("field1", "Text_Field_1", `Content-Type`(MediaType.text.plain))
      val field2 = Part.formData[IO]("field2", "Text_Field_2")

      multiparts
        .multipart(Vector(field1, field2))
        .flatMap { multipart =>
          val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
          val body = entity.body
          val request =
            Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

          mkDecoder.use { decoder =>
            val decoded = decoder.decode(request, true)
            val result = decoded.value

            assertIOBoolean(
              EitherT(result).semiflatMap(eqMultipartIO(_, multipart)).getOrElse(false)
            )
          }
        }
        .assert
    }

    test(s"Multipart form data $name should be encoded and decoded without content types") {
      val field1 = Part.formData[IO]("field1", "Text_Field_1")

      multiparts
        .multipart(Vector(field1))
        .flatMap { multipart =>
          val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
          val body = entity.body
          val request =
            Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

          mkDecoder.use { decoder =>
            val decoded = decoder.decode(request, true)
            val result = decoded.value

            assertIOBoolean(
              EitherT(result).semiflatMap(eqMultipartIO(_, multipart)).getOrElse(false)
            )
          }
        }
        .assert
    }

    test(s"Multipart form data $name should encoded and decoded with binary data") {
      val path = CrossPlatformResource("/ball.png")

      val field1 = Part.formData[IO]("field1", "Text_Field_1")
      val field2 = Part
        .fileData[IO]("image", path, `Content-Type`(MediaType.image.png))

      multiparts
        .multipart(Vector(field1, field2))
        .flatMap { multipart =>
          val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
          val body = entity.body
          val request =
            Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

          mkDecoder.use { decoder =>
            val decoded = decoder.decode(request, true)
            val result = decoded.value

            assertIOBoolean(
              EitherT(result).semiflatMap(eqMultipartIO(_, multipart)).getOrElse(false)
            )
          }
        }
        .assert
    }

    test(s"Multipart form data $name should be decoded and encode with content types") {
      val body =
        """
------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="text"

I AM A MOOSE
------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="file1"; filename="Graph_Databases_2e_Neo4j.pdf"
Content-Type: application/pdf


------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="file2"; filename="DataTypesALaCarte.pdf"
Content-Type: application/pdf


------WebKitFormBoundarycaZFo8IAKVROTEeD--
      """.replace("\n", "\r\n")
      val header = Headers(
        `Content-Type`(
          MediaType.multipartType("form-data", Some("----WebKitFormBoundarycaZFo8IAKVROTEeD"))
        )
      )
      val request = Request[IO](
        method = Method.POST,
        uri = url,
        body = Stream.emit(body).covary[IO].through(text.utf8.encode),
        headers = header,
      )

      mkDecoder.use { decoder =>
        val decoded = decoder.decode(request, true)
        val result = decoded.value.map(_.isRight)

        result.assertEquals(true)
      }
    }

    test(s"Multipart form data $name should be decoded and encoded without content types") {
      val body =
        """--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Mooses"

We are big mooses
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Moose"

I am a big moose
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz--

      """.replace("\n", "\r\n")
      val header = Headers(
        `Content-Type`(
          MediaType.multipartType("form-data", Some("bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz"))
        )
      )
      val request = Request[IO](
        method = Method.POST,
        uri = url,
        body = Stream.emit(body).through(text.utf8.encode),
        headers = header,
      )

      mkDecoder.use { decoder =>
        val decoded = decoder.decode(request, true)
        val result = decoded.value.map(_.isRight)

        result.assertEquals(true)
      }
    }

    test(s"Multipart form data $name should extract name properly if it is present") {
      val part = Part(
        Headers(`Content-Disposition`("form-data", Map(ci"name" -> "Rich Homie Quan"))),
        Stream.empty,
      )
      assertEquals(part.name, Some("Rich Homie Quan"))
    }

    test(s"Multipart form data $name should extract filename property if it is present") {
      val part = Part(
        Headers(
          `Content-Disposition`("form-data", Map(ci"name" -> "file", ci"filename" -> "file.txt"))
        ),
        Stream.empty,
      )
      assertEquals(part.filename, Some("file.txt"))
    }

    test(
      s"Multipart form data $name should include chunked transfer encoding header so that body is streamed by client"
    ) {
      multiparts.boundary.map { boundary =>
        val multipart = Multipart(Vector(), boundary)
        Request(method = Method.POST, uri = url, headers = multipart.headers).isChunked
      }.assert
    }

    test("Multipart should be encoded with a \\r\\n after the final part for robustness") {
      val field1 = Part.formData[IO]("bow", "wow")
      val multipart = Multipart[IO](Vector(field1), Boundary("arf"))
      val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
      val body = entity.body
      val request =
        Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)
      request.as[String].map(s => assert(s.endsWith("--arf--\r\n"), s))
    }

    test(s"$name should not wrap errors unrelated to multipart decoding") {
      object CustomError extends Throwable

      val badBody = Stream.raiseError[IO](CustomError)
      val multipart = Multipart[IO](Vector.empty, Boundary("boundary"))
      val request =
        Request(method = Method.POST, uri = url, body = badBody, headers = multipart.headers)

      mkDecoder.use(_.decode(request, true).value).intercept[CustomError.type]
    }
  }

  multipartSpec("with default decoder")(Resource.pure(implicitly))

  {
    @nowarn("cat=deprecation")
    val testDeprecated =
      multipartSpec("with mixed decoder")(Resource.pure(EntityDecoder.mixedMultipart[IO]()))
    testDeprecated
  }
  multipartSpec("with mixed resource decoder")(EntityDecoder.mixedMultipartResource[IO]())

  private def testPart[F[_]] = Part[F](Headers.empty, EmptyBody)

  test("Part.covary should disallow unrelated effects") {
    assert(
      compileErrors("testPart[Option].covary[IO]").nonEmpty
    )
  }

  test("Part.covary should allow related effects") {
    trait F1[A]
    trait F2[A] extends F1[A]
    testPart[F2].covary[F1]
  }

}
