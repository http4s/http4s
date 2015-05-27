package org.http4s

import scodec.bits.ByteVector

package object multipart {

  implicit class RichByteVector(bv: ByteVector) {
    def toStream(boundary: Boundary): Stream[ByteVector] = {
      println(s"stream bytevector on $boundary ___${bv.decodeUtf8}__")
      
      def byteVectorStream(boundary: Boundary, start: Int):Stream[ByteVector] = { 

            lazy val end   = {
                               val e = if (start == 0) bv.indexOfSlice(boundary.toBV, start)  
                                       else bv.indexOfSlice(boundary.toBV, start + boundary.lengthBV)
                               if(e == -1 ) bv.length else e         
                             }   
            if (start == -1 || start >= bv.length) Stream.empty
            else bv.slice(start,end) #:: byteVectorStream(boundary,end  + boundary.lengthBV )
        }      
      byteVectorStream(boundary,0) 
    }
  }

}