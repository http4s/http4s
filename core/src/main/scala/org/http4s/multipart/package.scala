package org.http4s

import org.http4s.util._
import scodec.bits.ByteVector

final case class Token(val value:String) {

  lazy val bv = ByteVector(value.getBytes)

  lazy val lengthBV = bv.length

}
package object multipart {
  
  val dash             = "--"
  //TODO:This needs to be a regular expression
  val transportPadding = "" 
  
  val delimiter:     Boundary => String =     boundary =>
                     new StringWriter()                <<
                     B.CRLF                            <<
                     dash                              << 
                     boundary.value              result()
                     
  val closeDelimiter:Boundary => String =     boundary =>
                     new StringWriter()                <<
                     delimiter(boundary)               <<
                     dash                        result()
                     
  val start:         Boundary => ByteVector = boundary =>
                     ByteVectorWriter()                <<
                     delimiter(boundary)               <<
                     transportPadding                  <<
                     B.CRLF                toByteVector()
                     
  val end:           Boundary => ByteVector = boundary =>
                     ByteVectorWriter()                <<
                     closeDelimiter(boundary)          <<
                     transportPadding                  <<
                     B.CRLF                toByteVector()

  val encapsulation: Boundary => String =     boundary =>
                     new StringWriter()                <<
                     delimiter(boundary)               <<
                     transportPadding                  <<
                     B.CRLF                      result()     
                      
                     
  implicit class RichByteVector(bv: ByteVector) {
    def toStream(token: Token): Stream[ByteVector] = {

      def byteVectorStream(start: Int):Stream[ByteVector] = { 

            lazy val end   = {
                               val e = if (start == 0) bv.indexOfSlice(token.bv, start)  
                                       else bv.indexOfSlice(token.bv, start + token.lengthBV)
                               if(e == -1 ) bv.length else e         
                             }   
            if (start == -1 || start >= bv.length) Stream.empty
            else bv.slice(start,end) #:: byteVectorStream(end  + token.lengthBV )
      }
      
      byteVectorStream(0) 
    }
  }

}