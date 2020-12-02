/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package multipart

import cats._
import cats.effect._
import cats.syntax.all._
import fs2._
import java.io.File
import org.http4s.headers._
import org.http4s.syntax.literals._
import org.http4s.EntityEncoder._
import org.specs2.mutable.Specification

class MultipartSpec extends Specification {
  implicit val contextShift: ContextShift[IO] = IO.contextShift(Http4sSpec.TestExecutionContext)

  val url = uri"https://example.com/path/to/some/where"

  implicit def partIOEq: Eq[Part[IO]] =
    Eq.instance[Part[IO]] { case (a, b) =>
      a.headers === b.headers && {
        for {
          abv <- a.body.compile.toVector
          bbv <- b.body.compile.toVector
        } yield abv === bbv
      }.unsafeRunSync()
    }

  implicit def multipartIOEq: Eq[Multipart[IO]] =
    Eq.instance[Multipart[IO]] { (a, b) =>
      a.headers === b.headers &&
      a.boundary === b.boundary &&
      a.parts === b.parts
    }

  def multipartSpec(name: String)(implicit
      E: EntityDecoder[IO, Multipart[IO]]): org.specs2.specification.core.Fragment = {
    s"Multipart form data $name" should {
      "be encoded and decoded with content types" in {
        val field1 =
          Part.formData[IO]("field1", "Text_Field_1", `Content-Type`(MediaType.text.plain))
        val field2 = Part.formData[IO]("field2", "Text_Field_2")
        val multipart = Multipart(Vector(field1, field2))
        val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
        val body = entity.body
        val request =
          Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)
        val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
        val result = decoded.value.unsafeRunSync()

        result must beRight.like { case mp =>
          mp === multipart
        }
      }

      "be encoded and decoded without content types" in {
        val field1 = Part.formData[IO]("field1", "Text_Field_1")
        val multipart = Multipart[IO](Vector(field1))

        val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
        val body = entity.body
        val request =
          Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)
        val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
        val result = decoded.value.unsafeRunSync()

        result must beRight.like { case mp =>
          mp === multipart
        }
      }

      "encoded and decoded with binary data" in {
        val file = new File(getClass.getResource("/ball.png").toURI)

        val field1 = Part.formData[IO]("field1", "Text_Field_1")
        val field2 = Part
          .fileData[IO]("image", file, Http4sSpec.TestBlocker, `Content-Type`(MediaType.image.png))

        val multipart = Multipart[IO](Vector(field1, field2))

        val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
        val body = entity.body
        val request =
          Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

        val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
        val result = decoded.value.unsafeRunSync()

        result must beRight.like { case mp =>
          mp === multipart
        }
      }

      "be decoded and encode with content types" in {
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
        val header = Headers.of(
          `Content-Type`(
            MediaType.multipartType("form-data", Some("----WebKitFormBoundarycaZFo8IAKVROTEeD"))))
        val request = Request[IO](
          method = Method.POST,
          uri = url,
          body = Stream.emit(body).covary[IO].through(text.utf8Encode),
          headers = header)

        val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
        val result = decoded.value.unsafeRunSync()

        result must beRight
      }

      "be decoded and encoded without content types" in {
        val body =
          """--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Mooses"

We are big mooses
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Moose"

I am a big moose
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz--

      """.replace("\n", "\r\n")
        val header = Headers.of(
          `Content-Type`(
            MediaType.multipartType("form-data", Some("bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz"))))
        val request = Request[IO](
          method = Method.POST,
          uri = url,
          body = Stream.emit(body).through(text.utf8Encode),
          headers = header)
        val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
        val result = decoded.value.unsafeRunSync()

        result must beRight
      }

      "extract name properly if it is present" in {
        val part = Part(
          Headers.of(`Content-Disposition`("form-data", Map("name" -> "Rich Homie Quan"))),
          Stream.empty.covary[IO])
        part.name must beEqualTo(Some("Rich Homie Quan"))
      }

      "extract filename property if it is present" in {
        val part = Part(
          Headers.of(
            `Content-Disposition`("form-data", Map("name" -> "file", "filename" -> "file.txt"))),
          Stream.empty.covary[IO]
        )
        part.filename must beEqualTo(Some("file.txt"))
      }

      "include chunked transfer encoding header so that body is streamed by client" in {
        val multipart = Multipart(Vector())
        val request = Request(method = Method.POST, uri = url, headers = multipart.headers)
        request.isChunked must beTrue
      }
    }
  }

  multipartSpec("with default decoder")(implicitly)
  multipartSpec("with mixed decoder")(MultipartDecoder.mixedMultipart[IO](Http4sSpec.TestBlocker))

  "Part" >> {
    def testPart[F[_]] = Part[F](Headers.empty, EmptyBody)
    "covary" should {
      "disallow unrelated effects" in {
        illTyped("testPart[Option].covary[IO]")
        true
      }

      "allow related effects" in {
        trait F1[A]
        trait F2[A] extends F1[A]
        testPart[F2].covary[F1]
        true
      }
    }
  }
}
