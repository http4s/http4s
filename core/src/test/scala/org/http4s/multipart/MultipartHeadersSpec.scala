package org.http4s
package multipart

import org.http4s._
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Http4s._
import org.http4s.Uri._
import org.http4s.util._
import scodec.bits.ByteVector
import org.specs2.SpecificationWithJUnit
import org.specs2.scalaz.DisjunctionMatchers
import scalaz._
import Scalaz._

class MultipartHeadersSpec extends SpecificationWithJUnit  with DisjunctionMatchers {

  def is = s2"""
   Can parse 
       an empty string                         $emptyHeaders               
       a single header                         $single
       a single header - no boundary           $singleNoBoundary
       multiple headers                        $multiple   
       multiple headers - no final boundary    $multipleNoFinalBoundary    
     """

  def emptyHeaders = {
    MultipartHeaders(ByteVector("".getBytes)) must beRightDisjunction
  }
  
  def single = {
    val expected = Some(`Content-Type`(`text/plain`))
    val result = MultipartHeaders(ByteVector("Content-Type: text/plain\r\n".getBytes))    
    result.map (_.get(`Content-Type`))  must beRightDisjunction(expected)   
  }

  def singleNoBoundary = {
    val expected = Some(`Content-Type`(`text/plain`))
    val result = MultipartHeaders(ByteVector("Content-Type: text/plain".getBytes))    
    result.map (_.get(`Content-Type`))  must beRightDisjunction(expected)   
  }  
 
 def multiple = {
       val textHeaders = """Content-Disposition: file; filename="file2.gif"""" + B.CRLF + 
                           "Content-Type: image/gif"                           + B.CRLF +
                           "Content-Transfer-Encoding: binary"                 + B.CRLF 
    
    MultipartHeaders(ByteVector(textHeaders.getBytes)) match {
         case -\/( error )   => ko(error.details) 
         case \/-( headers ) => headers.size              === 3                                                                  and 
                 headers.get(`Content-Type`)              === Some(`Content-Type`(`image/gif`))                                  and
                 headers.get(`Content-Disposition`)       === Some(`Content-Disposition`("file",Map("filename" -> "file2.gif"))) and
                 headers.get(`Content-Transfer-Encoding`) === Some(Header("Content-Transfer-Encoding","binary")) 
                 // `Content-Transfer-Encoding`  doesn't support parameters yet
                 
       }    
       
  }

 def multipleNoFinalBoundary = {
       val textHeaders = """Content-Disposition: file; filename="file2.gif"""" + B.CRLF + 
                         "Content-Type: image/gif"                             + B.CRLF +
                         "Content-Transfer-Encoding: binary"                    
    
    MultipartHeaders(ByteVector(textHeaders.getBytes)) match {
         case -\/( error )   => ko(error.details) 
         case \/-( headers ) => headers.size              === 3                                                                  and 
                 headers.get(`Content-Type`)              === Some(`Content-Type`(`image/gif`))                                  and
                 headers.get(`Content-Disposition`)       === Some(`Content-Disposition`("file",Map("filename" -> "file2.gif"))) and
                 headers.get(`Content-Transfer-Encoding`) === Some(Header("Content-Transfer-Encoding","binary")) 
                 // `Content-Transfer-Encoding`  doesn't support parameters yet
                 
       }    
       
  } 
 
}
