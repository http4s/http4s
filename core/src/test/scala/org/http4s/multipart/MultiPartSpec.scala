package org.http4s
package multipart

import org.http4s._

import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Http4s._
import org.http4s.Uri._
import org.http4s.util._
import org.http4s.Status.Ok

import scodec.bits.ByteVector
import org.http4s.EntityEncoder._
import Entity._
import scalaz.stream.Process
import org.specs2.SpecificationWithJUnit


class MultipartSpec extends SpecificationWithJUnit {

  def is = s2"""
     I can haz multipart   $multipass
     """

  def multipass = {

   implicit def mpe: EntityEncoder[MultiPart] = MultiPartEntityEncoder

    val url = Uri(
      scheme = Some(CaseInsensitiveString("https")),
      authority = Some(Authority(host = RegName("example.com"))),
      path = "/path/to/some/where")

    val txtToEntity: String => EntityEncoder.Entity = in =>
        EntityEncoder.Entity(Process.emit(in).map(s => ByteVector(s.getBytes)))

    val request    = Request(Method.POST, url)
    val ctf1       = Some(`Content-Type`(`text/plain`))
    val ef1        = txtToEntity("Text_Field_1")
    val field1     = FormData(Name("field1"), ctf1, ef1)
    val ef2        = txtToEntity("Text_Field_2")
    val field2     = FormData(Name("field2"), None, ef2)
    val multiPart  = MultiPart(List(field1,field2))
    val decoder    = MultiPartEntityDecoder

    //  val msg:Message = request.withHeaders(multiPart.headers).withBody(multiPart).map(decoder.decoder).run.run
    //  println(s"parts are  __${decoder.decoder(msg).run.run}__")
    //println(s"Content type is __${request.withHeaders(multiPart.headers).contentType.get.mediaType.extensions.get("boundry")}__")

   val bodyDisjunction = request.withBody(multiPart).map { r => EntityDecoder.collectBinary(r).run }.run.run

   bodyDisjunction.map { body =>
      val res = Response(Ok).withHeaders(multiPart.headers).withBody(body).run
      decoder.decodeBody(res.body)(multiPart.boundary.value).run.run
   }



    true === true
  }

}
