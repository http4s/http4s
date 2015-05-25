package org.http4s
package multipart

import org.http4s._
import parser._
import headers._
import Http4s._
import scalaz.concurrent._
import scalaz.concurrent.Task._
import java.nio.charset.{Charset => NioCharset, StandardCharsets}
import scodec.bits.ByteVector
import scalaz._
import Scalaz._
import scalaz.stream._

object MultiPartEntityDecoder {

  implicit def charset = StandardCharsets.UTF_8

  private val boundary: Message => Option[Boundary] = msg => msg.contentType.flatMap{ _.mediaType.extensions.get("boundry").map( Boundary) }

  val headerBodySeparator  = B.CRLFBV.++(B.CRLFBV)
  
  
  //TODO: Get Content-Type
  val toResponse:ByteVector => ParseFailure \/ Headers => DecodeResult[Part] = {body => parseResult =>
    val headersToResult:Headers => DecodeResult[Part]  = { h => 
        h.find { _.is(`Content-Disposition`) } match {
          case None => DecodeResult.failure(ParseFailure("Missing Content-Disposition header")) 
          case Some(cd:`Content-Disposition`) => 
            val part = FormData(Name(cd.name.toString()),None,EntityEncoder.Entity(Process.emit(body)))
            DecodeResult.success(part)   
          case other =>   DecodeResult.failure(ParseFailure(s"Missed $other"))
        }
    }    
    parseResult.fold(DecodeResult.failure[Part], headersToResult)   
  }
  
  val decodePart: ByteVector  => DecodeResult[Part] = { bv =>
    val endOfHeaders     = bv.indexOfSlice(headerBodySeparator)
    val (headersBV,body) = bv.splitAt(endOfHeaders)
    val headers          = MultipartHeaders(headersBV)
    toResponse(body)(headers)
  }

  
  val decodeMultiPartBody: Boundary => Message =>  Process[Task,Stream[DecodeResult[Part]]] = {boundary => message =>
    val pStream = message.body.map {  _.toStream(boundary) }
    pStream.map { _.map(decodePart)}
  }
  
  
  val decoder:EntityDecoder[MultiPart] =  EntityDecoder.decodeBy(MediaRange.`multipart/*`)( msg =>
      boundary(msg) match {
        case None           =>  DecodeResult.failure(ParseFailure("Missing boundary extention to Content-Type. " ,
                                                msg.contentType.map(_.toString).getOrElse("No Content-Type found.")))
        case Some(boundary) =>   
          val x: Process[Task,Stream[DecodeResult[Part]]]          = decodeMultiPartBody(boundary)(msg)
          val x1:Process[Task,Stream[Task[ParseFailure \/ Part]]] =  x.map{ _.map(_.fold(  _.left,  _.right))}
          val x2:Process[Task, ParseFailure \/ MultiPart]         = x1.flatMap { s => 
            val zero: ParseFailure \/ MultiPart = MultiPart(Seq.empty).right   
            val op:  (ParseFailure \/ MultiPart, Task[ParseFailure \/ Part]) => ParseFailure \/ MultiPart = { (acc,tp) =>
              val add:ParseFailure \/ MultiPart => ParseFailure \/ Part => ParseFailure \/ MultiPart =  { mp => p => (mp,p) match {
                case (-\/(f),_)  => mp
                case ( _,-\/(f)) => f.left[MultiPart]
                case ( \/-(mb),\/-(pb)) => mb.copy(parts = mb.parts :+ pb).right  
              }
              }
              
              tp.map{ add(acc) }.|> { _.run }
              
            }
            Process.emit(s.foldLeft(zero)(op))
          }
         val x3:Process[Task, ParseFailure \/ MultiPart]  => DecodeResult[MultiPart] = { p => 
          val y:Process[Task,   DecodeResult[MultiPart]]  = p.map { _.fold( DecodeResult.failure[MultiPart], DecodeResult.success[MultiPart])} 
          y.runLast.run.getOrElse(DecodeResult.failure[MultiPart](ParseFailure("Unable to decode MutliPart body")))
         }
          
         x3(x2)
      }
    )

}
