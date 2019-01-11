package org.http4s
package multipart

import java.io.File

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.headers._
import org.http4s.EntityEncoder._

class JVMMultipartSpec extends MultipartSpec with PlatformMultipartDecoder {
  def multipartFileSpec(name: String)(
      implicit E: EntityDecoder[IO, Multipart[IO]]): org.specs2.specification.core.Fragment = {
    s"Multipart form data $name" should {
      "encoded and decoded with binary data" in {

        val file = new File(getClass.getResource("/ball.png").toURI)

        val field1 = Part.formData[IO]("field1", "Text_Field_1")
        val field2 = Part.fileData[IO](
          "image",
          file,
          Http4sSpec.TestBlockingExecutionContext,
          `Content-Type`(MediaType.image.png))

        val multipart = Multipart[IO](Vector(field1, field2))

        val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
        val body = entity.body
        val request =
          Request(method = Method.POST, uri = url, body = body, headers = multipart.headers)

        val decoded = EntityDecoder[IO, Multipart[IO]].decode(request, true)
        val result = decoded.value.unsafeRunSync()

        result must beRight.like {
          case mp =>
            mp === multipart
        }
      }
    }
  }

  multipartFileSpec("with default decoder")(implicitly)
  multipartSpec("with mixed decoder")(MultipartDecoder.mixedMultipart[IO](scala.concurrent.ExecutionContext.global))
  multipartFileSpec("with mixed decoder")(MultipartDecoder.mixedMultipart[IO](scala.concurrent.ExecutionContext.global))
}
