package org.http4s

import scodec.bits.ByteVector

package object multipart {
  
  
  private val byteVectorStream: Boundary => Int => ByteVector => Stream[ByteVector] = { boundary =>
    start => bv =>
      lazy val end   = {
                         val e = if (start == 0) bv.indexOfSlice(boundary.toBV, start)  
                                 else bv.indexOfSlice(boundary.toBV, start + boundary.lengthBV)
                         if(e == -1 ) bv.length else e         
                       }   
      if (start == -1 || start >= bv.length) Stream.empty
      else bv.slice(start,end) #:: byteVectorStream(boundary)(end  + boundary.lengthBV )(bv)
  }
 

  implicit class RichByteVector(vector: ByteVector) {
    def toStream(boundary: Boundary): Stream[ByteVector] = byteVectorStream(boundary)(0)(vector)
  }

}