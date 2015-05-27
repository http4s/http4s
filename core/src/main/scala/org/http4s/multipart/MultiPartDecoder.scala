package org.http4s
package multipart

import org.http4s._
import parser._
import headers._
import Http4s._
import org.http4s.util._
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
  
  
  val toResult:ByteVector => ParseFailure \/ Headers => DecodeResult[Part] = {body => parseResult =>
    val headersToResult:Headers => DecodeResult[Part]  = { h =>
        
        h.get(`Content-Disposition`) match {
          case None => DecodeResult.failure(ParseFailure("Missing Content-Disposition header")) 
          case Some(cd:`Content-Disposition`) => 
            
            def success(name:String):DecodeResult[Part] = {
              val part = FormData(Name(name),h.get(`Content-Type`),EntityEncoder.Entity(Process.emit(body)))
              DecodeResult.success(part)
            }
            cd.parameters.get("name").fold[DecodeResult[Part]](DecodeResult.failure(ParseFailure("Unable to decode MutliPart body")))(success)
        }
    }    
    
    parseResult.fold(DecodeResult.failure[Part], headersToResult)   
  }
  
  val decodePart: ByteVector  => DecodeResult[Part] = { bv =>
    val endOfHeaders     = bv.indexOfSlice(headerBodySeparator)
    val (headersBV,body) = bv.splitAt(endOfHeaders)
    val headers          = MultipartHeaders(headersBV)
    println(s"part ${headersBV.decodeUtf8}")
    toResult(body)(headers)
  }

  
  val decodeMultipartBody: Boundary => Message =>  Process[Task,Stream[DecodeResult[Part]]] = {
    boundary => message =>
    val delimiter          = new StringWriter()             <<
                             B.CRLF                         <<
                             MultipartDefinition.dash       << 
                             boundary.value           result()
    val close_delimiter    = new StringWriter()             <<
                             delimiter                      <<
                             MultipartDefinition.dash result()
                             
    //TODO:This needs to be a regular expression
    val transport_padding  = " "
    val start              = ByteVectorWriter() <<
                             delimiter          <<
                             transport_padding  <<
                             B.CRLF toByteVector()
    val end                = ByteVectorWriter() <<
                             close_delimiter    <<
                             transport_padding  <<
                             B.CRLF toByteVector()
    val encapsulation      = new StringWriter() <<
                             delimiter          <<
                             transport_padding  <<
                             B.CRLF result()                              
    val body = message.body
    
    body.map { 
         _.
         drop(start.length).
         toStream(Boundary(encapsulation)).
         map(decodePart)
    }
  }
  
  
  val decoder:EntityDecoder[MultiPart] =  EntityDecoder.decodeBy(MediaRange.`multipart/*`)( msg =>
      boundary(msg) match {
        case None           =>  DecodeResult.failure(ParseFailure("Missing boundary extention to Content-Type. " ,
                                                msg.contentType.map(_.toString).getOrElse("No Content-Type found.")))
        case Some(boundary) =>   
          println(s"some boundary $boundary")
          val x: Process[Task,Stream[DecodeResult[Part]]]          = decodeMultipartBody(boundary)(msg)
          val x1:Process[Task,Stream[Task[ParseFailure \/ Part]]] =  x.map{ _.map(_.fold(  _.left,  _.right))}
          val x2:Process[Task, ParseFailure \/ MultiPart]         = x1.flatMap { s => 
            val zero: ParseFailure \/ MultiPart = MultiPart(Seq.empty,boundary).right   
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
