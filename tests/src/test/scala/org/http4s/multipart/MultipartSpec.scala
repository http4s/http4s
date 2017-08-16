package org.http4s
package multipart

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import org.http4s._
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Uri._
import org.http4s.util._
import org.http4s.Status.Ok
import org.http4s.EntityEncoder._
import Entity._
import org.specs2.Specification
import org.specs2.matcher.DisjunctionMatchers

import cats._
import cats.implicits._
import fs2._

import scodec.bits.BitVector
import scodec.bits.ByteVector

class MultipartSpec extends Specification with DisjunctionMatchers {
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
      scheme = Some(CaseInsensitiveString("https")),
      authority = Some(Authority(host = RegName("example.com"))),
      path = "/path/to/some/where")

  def toBV(entityBody: EntityBody): ByteVector = ByteVector(entityBody.runLog.unsafeRun())

  implicit def partEq : Eq[Part] = Eq.instance[Part] { case (a, b) =>
    a.headers === b.headers &&
      {
        for {
          abv <- a.body.runLog.map(ByteVector(_))
          bbv <- b.body.runLog.map(ByteVector(_))
        } yield abv === bbv
      }.unsafeRun()

  }

  implicit val multipartEq : Eq[Multipart] = Eq.instance[Multipart]{ (a, b) =>
    a.headers === b.headers &&
      a.boundary === b.boundary &&
      a.parts === b.parts
  }


  def encodeAndDecodeMultipart = {

    val field1     = Part.formData("field1", "Text_Field_1", `Content-Type`(`text/plain`))
    val field2     = Part.formData("field2", "Text_Field_2")
    val multipart  = Multipart(Vector(field1,field2))
    val entity     = EntityEncoder[Multipart].toEntity(multipart)
    val body       = entity.unsafeRun().body
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = body,
                             headers = multipart.headers )
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.value.unsafeRun

    result must beRight.like { case mp =>
      mp === multipart
    }
  }

  def encodeAndDecodeMultipartMissingContentType = {

    val field1     = Part.formData("field1", "Text_Field_1")
    val multipart  = Multipart(Vector(field1))

    val entity     = EntityEncoder[Multipart].toEntity(multipart)
    val body       = entity.unsafeRun().body
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = body,
                             headers = multipart.headers )
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.value.unsafeRun()

    result must beRight.like { case mp =>
        mp === multipart
    }

  }

  def encodeAndDecodeMultipartWithBinaryFormData = {

    val file       = new File(getClass.getResource("/Animated_PNG_example_bouncing_beach_ball.png").toURI)

    val field1     = Part.formData("field1", "Text_Field_1")

    val ef2        = fileToEntity(file)
    val field2     = Part.fileData("image", file, `Content-Type`(`image/png`))

    val multipart  = Multipart(Vector(field1,field2))

    val entity     = EntityEncoder[Multipart].toEntity(multipart)
    val body       = entity.unsafeRun().body
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = body,
                             headers = multipart.headers )

    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.value.unsafeRun()

    result must beRight.like { case mp =>
      mp === multipart
    }
  }


  def decodeMultipartRequestWithContentTypes = {

    val body       ="""
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
    val header     = Headers(`Content-Type`(MediaType.multipart("form-data", Some("----WebKitFormBoundarycaZFo8IAKVROTEeD"))))
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Stream.emit(body).covary[Task].through(text.utf8Encode),
                             headers = header)

    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.value.unsafeRun()

   result must beRight
  }


  def decodeMultipartRequestWithoutContentTypes = {

    val body       =
"""--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Mooses"

We are big mooses
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Moose"

I am a big moose
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz--

      """.replaceAllLiterally("\n", "\r\n")
    val header     = Headers(`Content-Type`(MediaType.multipart("form-data", Some("bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz"))))
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Stream.emit(body).covary[Task].through(text.utf8Encode),
                             headers = header)
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.value.unsafeRun()

   result must beRight
  }

  def extractNameIfPresent = {
    val part = Part(Headers(`Content-Disposition`("form-data",Map("name" -> "Rich Homie Quan"))), Stream.empty[Task, Byte])
    part.name must beEqualTo(Some("Rich Homie Quan"))
  }

  private def fileToEntity(f: File): Entity = {
    val bitVector = BitVector.fromMmap(new java.io.FileInputStream(f).getChannel)
    Entity(body = Stream.emits(ByteVector(bitVector.toBase64.getBytes).toSeq))
  }

}
