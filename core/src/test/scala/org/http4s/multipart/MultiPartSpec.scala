package org.http4s
package multipart

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


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
import org.specs2.Specification
import org.specs2.scalaz.DisjunctionMatchers


class MultipartSpec extends Specification  with DisjunctionMatchers {

  def mpe: EntityEncoder[Multipart] = MultipartEntityEncoder
  def mpd: EntityDecoder[Multipart] = MultipartEntityDecoder.decoder
  
  
//      encoded and decoded example A       $encodeAndDecodeA
//      encoded and decoded example B       $encodeAndDecodeB
//      encoded and decoded example C       $encodeAndDecodeC
  
  def is = s2"""
    Multipart form data can be 
      encoded and decoded example D       $encodeAndDecodeD
      encoded and decoded example E       $encodeAndDecodeE      
     """
  val url = Uri(
      scheme = Some(CaseInsensitiveString("https")),
      authority = Some(Authority(host = RegName("example.com"))),
      path = "/path/to/some/where")

  val txtToEntity: String => EntityEncoder.Entity = in =>
      EntityEncoder.Entity(Process.emit(in).map(s => ByteVector(s.getBytes)))
   
  def encodeAndDecodeA = {

    val ctf1       = Some(`Content-Type`(`text/plain`))
    val ef1        = txtToEntity("Text_Field_1")
    val field1     = FormData(Name("field1"), ctf1, ef1)
    val ef2        = txtToEntity("Text_Field_2")
    val field2     = FormData(Name("field2"), None, ef2)
    val multipart  = Multipart(List(field1,field2))
    val entity     = mpe.toEntity(multipart)
    val body       = entity.run.body.runLog.run.fold(ByteVector.empty)((acc,x) => acc ++ x )
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body),
                             headers = multipart.headers )
          
    val decoded    = mpd.decode(request)
    val result     = decoded.run.run

    
    result must beRightDisjunction(multipart)
  }

  def encodeAndDecodeB = {

    val ef1        = txtToEntity("Text_Field_1")
    val field1     = FormData(Name("field1"), None, ef1)
    val multipart  = Multipart(List(field1))

    val entity     = mpe.toEntity(multipart)
    val body       = entity.run.body.runLog.run.fold(ByteVector.empty)((acc,x) => acc ++ x )
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body),
                             headers = multipart.headers )
          
    val decoded    = mpd.decode(request)
    val result     = decoded.run.run

    
    result must beRightDisjunction(multipart)
  }

  def encodeAndDecodeC = {

    val path       = "./core/src/test/resources/Animated_PNG_example_bouncing_beach_ball.png"
    val file       = new File(path)
    
    val ef1        = txtToEntity("Text_Field_1")
    val field1     = FormData(Name("field1"), None, ef1)
    
    val ctf2       = Some(`Content-Type`(`image/png`))
    val ef2        = fileToEntity(file,65557)
    val field2     = FormData(Name("image"), ctf2, ef2)
    
    
    val multipart  = Multipart(List(field1,field2))
    
    val entity     = mpe.toEntity(multipart)
    val body       = entity.run.body.runLog.run.fold(ByteVector.empty)((acc,x) => acc ++ x )
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body),
                             headers = multipart.headers )
          
    val decoded    = mpd.decode(request)
    val result     = decoded.run.run
    
    result must beRightDisjunction(multipart)
  }

  def encodeAndDecodeD = {

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
      """
    val header     = Headers(`Content-Type`(MediaType.multipart("form-data", Some("----WebKitFormBoundarycaZFo8IAKVROTEeD"))))
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body).map(s => ByteVector(s.getBytes)),
                             headers = header)

    val decoded    = mpd.decode(request)
    val result     = decoded.run.run
    
   result must beRightDisjunction
  }

  
  def encodeAndDecodeE = {

    val body       = 
"""--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Mooses"

We are big mooses
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz
Content-Disposition: form-data; name="Moose"

I am a big moose
--bQskVplbbxbC2JO8ibZ7KwmEe3AJLx_Olz--
      
      """
    val header     = Headers(`Content-Type`(MediaType.multipart("form-data", Some("----WebKitFormBoundarycaZFo8IAKVROTEeD"))))
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body).map(s => ByteVector(s.getBytes)),
                             headers = header)

    val decoded    = mpd.decode(request)
    val result     = decoded.run.run
    
   result must beRightDisjunction
  }  

 
  private def fileToEntity(f: File,length:Int): Entity = {
    val fis   = new FileInputStream(f)
    val array = new Array[Byte](length)
    fis.read(array)
    fis.close()
    Entity(body = Process.emit(ByteVector(array)))
  }  
  
}
