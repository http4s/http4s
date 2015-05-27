package org.http4s
package multipart

import scodec.bits.ByteVector
import org.specs2.SpecificationWithJUnit
import org.specs2.matcher.MatchFailure


class ByteVectorStreamSpec extends SpecificationWithJUnit {

  
  def is = s2"""
   ByteVector Stream can handle:
   missing boundary                       $noBoundary               
   single element with boundary           $oneElementBoundary               
   multiple elements                      $multipleElementsNoEndBoundary
   multiple elements, trailing boundary   $multipleElementsTrailingEndBoundary   
   multipart headers example              $headersExample
   """

   
  def noBoundary = {
    val input      = "I have no boundaries"
    val byteVector = ByteVector(input.getBytes)
    val result     = byteVector.toStream(Boundary(B.CRLF))

    result match {
      case output #:: xs => 
                    output === byteVector and xs === Stream.empty
      case _      => ko
    }
  }

  
  def oneElementBoundary = {
    val input      = ByteVector("I have a boundary".getBytes)
    val byteVector = input ++  B.CRLFBV 
    val result     = byteVector.toStream(Boundary(B.CRLF))

    result match {
      case output #:: xs =>
                    output === input and xs === Stream.empty
      case _      => ko
    }
  }

  
  def multipleElementsNoEndBoundary = {
    val input      = ByteVector("I have a boundaries".getBytes)
    val byteVector = input ++  B.CRLFBV ++ input ++  B.CRLFBV  ++ input 
    val result     = byteVector.toStream(Boundary(B.CRLF))
    result.length === 3
    result match {
      case output #:: xs =>
                    output === input  
      case _      => ko
    }
  }  

  
  def multipleElementsTrailingEndBoundary = {
    val input      = ByteVector("I have a boundaries".getBytes)
    val byteVector = input ++  B.CRLFBV ++ input ++  B.CRLFBV  ++ input  ++  B.CRLFBV 
    val result     = byteVector.toStream(Boundary(B.CRLF))

    result.length === 3
    result match {
      case output #:: xs =>
                    output === input  
      case _      => ko
    }
  }
  
  
  def headersExample = {
    val textHeaders = """Content-Disposition: file; filename="file2.gif"""" + B.CRLF + 
                        "Content-Type: image/gif"                           + B.CRLF +
                        "Content-Transfer-Encoding: binary"                 + B.CRLF    
    val input      = ByteVector(textHeaders.getBytes) 
    val result     = input.toStream(Boundary(B.CRLF))
    
    result.foreach( v => println(s"__${v.decodeUtf8}__"))

    result.length === 3
    
  } 
  
  
}
