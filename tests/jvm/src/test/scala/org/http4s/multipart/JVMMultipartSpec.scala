package org.http4s
package multipart

import java.io.File

import org.http4s._
import cats.effect._
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Headers._
import org.http4s.Uri._
import org.http4s.util._
import org.http4s.EntityEncoder._
import org.specs2.Specification

import cats._
import cats.implicits._

import scodec.bits.ByteVector

class JVMMultipartSpec extends Specification {
  sequential

  def is = s2"""
    Multipart form data can be
        encoded and decoded with    binary data    $encodeAndDecodeMultipartWithBinaryFormData
     """

  val url = Uri(
    scheme = Some(CaseInsensitiveString("https")),
    authority = Some(Authority(host = RegName("example.com"))),
    path = "/path/to/some/where")

  implicit def partIOEq: Eq[Part[IO]] = Eq.instance[Part[IO]] {
    case (a, b) =>
      a.headers === b.headers && {
        for {
          abv <- a.body.runLog.map(ByteVector(_))
          bbv <- b.body.runLog.map(ByteVector(_))
        } yield abv === bbv
      }.unsafeRunSync()
  }

  implicit def multipartIOEq: Eq[Multipart[IO]] = Eq.instance[Multipart[IO]] { (a, b) =>
    a.headers === b.headers &&
    a.boundary === b.boundary &&
    a.parts === b.parts
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

}
