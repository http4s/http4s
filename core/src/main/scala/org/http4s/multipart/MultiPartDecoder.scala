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

object MultipartEntityDecoder {

  implicit def charset = StandardCharsets.UTF_8

  private val boundary: Message => Option[Boundary] = msg => msg.contentType.flatMap{ _.mediaType.extensions.get("boundary").map( Boundary) }

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
    val (headersBV,body) = bv  splitAt(endOfHeaders)
    val headers          = MultipartHeaders(headersBV)
    toResult(body.drop(headerBodySeparator.length))(headers)
  }

  
  
  val decodeMultipartBody: Boundary => Message =>  Process[Task,Stream[DecodeResult[Part]]] = {
    boundary => message =>          
    message.body.map { _.
         drop(start(boundary).length).
         dropRight(end(boundary).length).
         toStream(Token(encapsulation(boundary))).
         map(decodePart)
    }
  }
  
  
  val decoder:EntityDecoder[Multipart] =  EntityDecoder.decodeBy(MediaRange.`multipart/*`)( msg =>
      boundary(msg) match {
        case None           =>  DecodeResult.failure(ParseFailure("Missing boundary extention to Content-Type. " ,
                                                msg.contentType.map(_.toString).getOrElse("No Content-Type found.")))
        case Some(boundary) =>   
          val x: Process[Task,Stream[DecodeResult[Part]]]         = decodeMultipartBody(boundary)(msg)
          val x1:Process[Task,Stream[Task[ParseFailure \/ Part]]] =  x.map{ _.map(_.fold(  _.left,  _.right))}
          val x2:Process[Task, ParseFailure \/ Multipart]         = x1.flatMap { s => 
            val zero: ParseFailure \/ Multipart = Multipart(Seq.empty,boundary).right   
            val op:  (ParseFailure \/ Multipart, Task[ParseFailure \/ Part]) => ParseFailure \/ Multipart = { (acc,tp) =>
              val add:ParseFailure \/ Multipart => ParseFailure \/ Part => ParseFailure \/ Multipart =  { mp => p => (mp,p) match {
                case (-\/(f),_)  => mp
                case ( _,-\/(f)) => f.left[Multipart]
                case ( \/-(mb),\/-(pb)) => mb.copy(parts = mb.parts :+ pb).right  
              }
              }
              
              tp.map{ add(acc) }.|> { _.run }
              
            }
            Process.emit(s.foldLeft(zero)(op))
          }
         val x3:Process[Task, ParseFailure \/ Multipart]  => DecodeResult[Multipart] = { p => 
          val y:Process[Task,   DecodeResult[Multipart]]  = p.map { _.fold( DecodeResult.failure[Multipart], DecodeResult.success[Multipart])} 
          y.runLast.run.getOrElse(DecodeResult.failure[Multipart](ParseFailure("Unable to decode MutliPart body")))
         }
          
         x3(x2)
      }
    )

}
