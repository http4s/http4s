package org.http4s
package multipart

import cats._
import cats.effect._
import cats.implicits._
import fs2._
import java.io.File
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Uri._
import org.http4s.EntityEncoder._
import org.specs2.Specification
import scodec.bits.ByteVector

class MultipartSpec extends Specification {
  sequential

  def is = s2"""
    Multipart form data can be
        encoded and decoded with    content types  $encodeAndDecodeMultipart
        encoded and decoded without content types  $encodeAndDecodeMultipartMissingContentType
        encoded and decoded with    binary data    $encodeAndDecodeMultipartWithBinaryFormData
        decode  and encode  with    content types  $decodeMultipartRequestWithContentTypes
        decode  and encode  without content types  $decodeMultipartRequestWithoutContentTypes
        extract name properly if it is present     $extractNameIfPresent
     """

  val url = Uri(
    scheme = Some(Scheme.https),
    authority = Some(Authority(host = RegName("example.com"))),
    path = "/path/to/some/where")

  def toBV(entityBody: EntityBody[IO]): ByteVector =
    ByteVector(entityBody.compile.toVector.unsafeRunSync())

  implicit def partIOEq: Eq[Part[IO]] = Eq.instance[Part[IO]] {
    case (a, b) =>
      a.headers === b.headers && {
        for {
          abv <- a.body.compile.toVector.map(ByteVector(_))
          bbv <- b.body.compile.toVector.map(ByteVector(_))
        } yield abv === bbv
      }.unsafeRunSync()
  }

  implicit def multipartIOEq: Eq[Multipart[IO]] = Eq.instance[Multipart[IO]] { (a, b) =>
    a.headers === b.headers &&
    a.boundary === b.boundary &&
    a.parts === b.parts
  }

  def encodeAndDecodeMultipart = {

    val field1 = Part.formData[IO]("field1", "Text_Field_1", `Content-Type`(`text/plain`))
    val field2 = Part.formData[IO]("field2", "Text_Field_2")
    val multipart = Multipart(Vector(field1, field2))
    val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
    val body = entity.unsafeRunSync().body
    val request = Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)
    val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
    val result = decoded.value.unsafeRunSync()

    result must beRight.like {
      case mp =>
        mp === multipart
    }
  }

  def encodeAndDecodeMultipartMissingContentType = {

    val field1 = Part.formData[IO]("field1", "Text_Field_1")
    val multipart = Multipart[IO](Vector(field1))

    val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
    val body = entity.unsafeRunSync().body
    val request = Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)
    val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
    val result = decoded.value.unsafeRunSync()

    result must beRight.like {
      case mp =>
        mp === multipart
    }

  }

  def encodeAndDecodeMultipartWithBinaryFormData = {

    val file = new File(getClass.getResource("/ball.png").toURI)

    val field1 = Part.formData[IO]("field1", "Text_Field_1")
    val field2 = Part.fileData[IO]("image", file, `Content-Type`(`image/png`))

    val multipart = Multipart[IO](Vector(field1, field2))

    val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
    val body = entity.unsafeRunSync().body
    val request = Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

    val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
    val result = decoded.value.unsafeRunSync()

    result must beRight.like {
      case mp =>
        mp === multipart
    }
  }

  def decodeMultipartRequestWithContentTypes = {

    val body = """
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
      """.replaceAllLiterally("\n", "\r\n")
    val header = Headers(
      `Content-Type`(
        MediaType.multipart("form-data", Some("----WebKitFormBoundarycaZFo8IAKVROTEeD"))))
    val request = Request[IO](
      method = Method.POST,
      uri = url,
      body = Stream.emit(body).through(text.utf8Encode),
      headers = header)

    val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
    val result = decoded.value.unsafeRunSync()

    result must beRight
  }

  def decodeMultipartRequestWithoutContentTypes = {

    val body =
      """--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Mooses"

We are big mooses
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Moose"

I am a big moose
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz--

      """.replaceAllLiterally("\n", "\r\n")
    val header = Headers(
      `Content-Type`(MediaType.multipart("form-data", Some("bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz"))))
    val request = Request[IO](
      method = Method.POST,
      uri = url,
      body = Stream.emit(body).through(text.utf8Encode),
      headers = header)
    val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
    val result = decoded.value.unsafeRunSync()

    result must beRight
  }

  def extractNameIfPresent = {
    val part = Part(
      Headers(`Content-Disposition`("form-data", Map("name" -> "Rich Homie Quan"))),
      Stream.empty.covary[IO])
    part.name must beEqualTo(Some("Rich Homie Quan"))
  }
}
