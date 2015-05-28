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

  implicit def mpe: EntityEncoder[Multipart] = MultipartEntityEncoder
    
  
  def is = s2"""
    Multipart form data can be 
      encoded and decoded example A       $encodeAndDecodeA
      encoded and decoded example B       $encodeAndDecodeB
      encoded and decoded example C       $encodeAndDecodeC
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
    val entity     = MultipartEntityEncoder.toEntity(multipart)
    val body       = entity.run.body.runLog.run.fold(ByteVector.empty)((acc,x) => acc ++ x )
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body),
                             headers = multipart.headers )
          
    val decoder    = MultipartEntityDecoder.decoder
    val decoded    = decoder.decode(request)
    val result     = decoded.run.run

    
    result must beRightDisjunction(multipart)
  }

  def encodeAndDecodeB = {

    val ef1        = txtToEntity("Text_Field_1")
    val field1     = FormData(Name("field1"), None, ef1)
    val multipart  = Multipart(List(field1))

    val entity     = MultipartEntityEncoder.toEntity(multipart)
    val body       = entity.run.body.runLog.run.fold(ByteVector.empty)((acc,x) => acc ++ x )
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body),
                             headers = multipart.headers )
          
    val decoder    = MultipartEntityDecoder.decoder
    val decoded    = decoder.decode(request)
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
    
    val entity     = MultipartEntityEncoder.toEntity(multipart)
    val body       = entity.run.body.runLog.run.fold(ByteVector.empty)((acc,x) => acc ++ x )
    val request    = Request(method  = Method.POST,
                             uri     = url,
                             body    = Process.emit(body),
                             headers = multipart.headers )
          
    val decoder    = MultipartEntityDecoder.decoder
    val decoded    = decoder.decode(request)
    val result     = decoded.run.run

    
//    Very evil way to confirm we are getting back sensible bytes.    
//    result.map { m =>
//      val part:FormData = m.parts.drop(1).head.asInstanceOf[FormData]
//      part.content.body.map{ bv =>
//        val out = File.createTempFile("prefix", "suffix")
//        val fos = new FileOutputStream(out )
//        fos.write(bv.toArray)
//        fos.flush()
//        fos.close()
//        println(out.getCanonicalPath)
//        println(out.getAbsoluteFile)
//      }.run
//    }
    
    result must beRightDisjunction(multipart)
  }

  private def fileToEntity(f: File,length:Int): Entity = {
    val fis   = new FileInputStream(f)
    val array = new Array[Byte](length)
    fis.read(array)
    fis.close()
    Entity(body = Process.emit(ByteVector(array)))
  }  
  
}
