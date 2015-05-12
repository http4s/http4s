package org.http4s
package multipart

import org.http4s._
import parser._
import headers.`Content-Type`
import Http4s._
import scalaz.concurrent._
import scalaz.concurrent.Task._
import java.nio.charset.{Charset => NioCharset, StandardCharsets}
import scodec.bits.ByteVector
import scalaz.Monoid

object MultiPartEntityDecoder {

  implicit def charset = StandardCharsets.UTF_8

  private val boundary: Message => Option[Boundary] = msg => msg.contentType.flatMap{ _.mediaType.extensions.get("boundry").map( Boundary) }

  val decodePart: ByteVector => DecodeResult[Part] = vector =>
    ???


  val decodeByteVectorBody: Boundary => ByteVector  => DecodeResult[MultiPart] = { boundary => bv =>

      val start = bv.indexOfSlice(boundary.toBV)
      val end   = bv.indexOfSlice(boundary.toBV,start + boundary.lengthBV)
      val slice = bv.slice(start + boundary.lengthBV ,end - MultiPartDefinition.dash.length() )

      val zero  = MultiPart(parts = List(),boundary = boundary)
      val accumulator: (Part, => MultiPart ) => MultiPart =  (part,acc) => acc.copy(parts =  acc.parts.+:(part) )




//       decodePart(slice).foldRight( zero )( accumulator )

/*

scalaz.stream.Process[scalaz.concurrent.Task,org.http4s.DecodeResult[org.http4s.multipart.MultiPart]]
scalaz.stream.Process[scalaz.concurrent.Task,scalaz.EitherT[scalaz.concurrent.Task,org.http4s.ParseFailure,org.http4s.multipart.MultiPart]]
org.http4s.DecodeResult[org.http4s.multipart.MultiPart]     (which expands to)  scalaz.EitherT[scalaz.concurrent.Task,org.http4s.ParseFailure,org.http4s.multipart.MultiPart]
 * *
 *
 *
 */



      ???
  }

  val decodeMultiPartBody: Boundary => ByteVector =>  DecodeResult[MultiPart] = {boundary => bv =>
     
    ???
  }



  val decoder:EntityDecoder[MultiPart] =  EntityDecoder.decodeBy(MediaRange.`multipart/*`)( msg =>
      boundary(msg) match {
        case None           =>  DecodeResult.failure(ParseFailure("Missing boundary extention to Content-Type. " ,
                                                msg.contentType.map(_.toString).getOrElse("No Content-Type found.")))
        case Some(boundary) =>
                                       msg.body.map(decodeMultiPartBody(boundary)).run


      }
    )

}
