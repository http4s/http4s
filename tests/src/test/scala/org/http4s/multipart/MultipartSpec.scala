package org.http4s
package multipart

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


import org.http4s._
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.internal.compatibility._
import org.http4s.Http4s._
import org.http4s.Uri._
import org.http4s.util._
import org.http4s.Status.Ok
import scalaz.Equal
import scalaz.concurrent.Task
import scodec.bits._

import org.http4s.EntityEncoder._
import Entity._
import scalaz.stream.Process
import scalaz.std.string._
import scalaz.std.vector._
import scalaz.stream.text._
import scalaz.syntax.equal._
import org.specs2.Specification
import org.specs2.matcher.DisjunctionMatchers


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

  implicit lazy val MultiPartEq: Equal[Multipart] =
    Equal.equalBy[Multipart, Vector[Part]](_.parts)

  // a.headers == b.headers doesn't do what I expected.
  implicit lazy val PartEq: Equal[Part] =
    Equal.equal { (a, b) => a.headers.size == b.headers.size && (a.headers zip b.headers).forall { case (ah, bh) => ah == bh } && a.body === b.body }

  // This one is shady.
  implicit lazy val EntityBodyEq: Equal[EntityBody] =
    Equal.equalBy[EntityBody, String](_.pipe(utf8Decode).runFoldMap(identity).unsafePerformSync)

  def encodeAndDecodeMultipart = {

    val field1     = Part.formData("field1", "Text_Field_1", `Content-Type`(`text/plain`))
    val field2     = Part.formData("field2", "Text_Field_2")
    val multipart  = Multipart(Vector(field1,field2))
    val entity     = EntityEncoder[Multipart].toEntity(multipart)
    val body       = entity.unsafePerformSync.body
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = body,
                             headers = multipart.headers )
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.run.unsafePerformSync
    
    result must be_\/-.like { case mp => mp must beTypedEqualTo(multipart, Equal[Multipart].equal) }
  }

  def encodeAndDecodeMultipartMissingContentType = {

    val field1     = Part.formData("field1", "Text_Field_1")
    val multipart  = Multipart(Vector(field1))

    val entity     = EntityEncoder[Multipart].toEntity(multipart)
    val body       = entity.unsafePerformSync.body
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = body,
                             headers = multipart.headers )                             
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.run.unsafePerformSync

    result must be_\/-.like { case mp => mp must beTypedEqualTo(multipart, Equal[Multipart].equal) }
  }

  def encodeAndDecodeMultipartWithBinaryFormData = {

    val file       = new File(getClass.getResource("/Animated_PNG_example_bouncing_beach_ball.png").toURI)

    val field1     = Part.formData("field1", "Text_Field_1")
    
    val ef2        = fileToEntity(file)
    val field2     = Part.fileData("image", file, `Content-Type`(`image/png`))
    
    val multipart  = Multipart(Vector(field1,field2))
    
    val entity     = EntityEncoder[Multipart].toEntity(multipart)
    val body       = entity.unsafePerformSync.body
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = body,
                             headers = multipart.headers )
                                       
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.run.unsafePerformSync

    result must be_\/-.like { case mp => multipart === mp }
  }

  def decodeMultipartRequestWithContentTypes = {

    val body       = """
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
                             body    = Process.emit(body).pipe(utf8Encode),
                             headers = header)

    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.run.unsafePerformSync
    
   result must be_\/-
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
                             body    = Process.emit(body).pipe(utf8Encode),
                             headers = header)
    val decoded    = EntityDecoder[Multipart].decode(request, true)
    val result     = decoded.run.unsafePerformSync
    
   result must be_\/-
  }  

  def extractNameIfPresent = {
    val part = Part(Headers(`Content-Disposition`("form-data",Map("name" -> "Rich Homie Quan"))), Process.halt)
    part.name must beEqualTo(Some("Rich Homie Quan"))
  }

  private def fileToEntity(f: File): Entity = {
    val bitVector = BitVector.fromMmap(new java.io.FileInputStream(f).getChannel)
    Entity(body = Process.emit(ByteVector(bitVector.toBase64.getBytes)))
  }  
  
}
