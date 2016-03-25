package org.http4s
package multipart

import scala.util.{Try, Success, Failure}
import scala.util.control._
import org.http4s._
import parser._
import headers._
import Http4s._
import org.http4s.util._
import scalaz.concurrent._
import scalaz.concurrent.Task._

import scodec.bits.ByteVector
import scalaz._
import Scalaz._
import scalaz.stream._
import org.parboiled2._
import org.log4s.getLogger


object MultipartEntityDecoder {

  private[this] val logger = getLogger
  
  private val boundary: Message => Option[Boundary] = msg => msg.contentType.flatMap{ _.mediaType.extensions.get("boundary").map( Boundary(_) ) }

  val decoder:EntityDecoder[Multipart] =  EntityDecoder.decodeBy(MediaRange.`multipart/*`)( msg =>
      boundary(msg) match {
        case None           =>  
          DecodeResult.failure(InvalidMessageBodyFailure("Missing boundary extention to Content-Type"))
        case Some(boundary) =>  
          val parseResult:Option[Try[Seq[ParseFailure] \/ Multipart]] = {            
            //TODO: This is incredibly uncool, we are folding the entire body into one ByteVector
            msg.body.fold(ByteVector.empty)( (acc,p) =>  acc ++ p).map { body =>
                  MultipartParser(body.toArray, boundary).MultipartBody.run()
              }.runLast.run
          } 
          
          val full:Try[Seq[ParseFailure] \/ Multipart] => DecodeResult[Multipart] = { tri =>
            tri match {
              case Success(\/-(mp))  => 
                DecodeResult.success(mp)
              case Success(-\/(pfs)) =>
                val failure = pfs.foldLeft(InvalidMessageBodyFailure(""))(
                    (acc,pf) => acc.copy(details= s"${acc.details}${pf.sanitized}\n${pf.details}\n\n"))
                DecodeResult.failure(failure)
              case Failure(t)        =>  
                DecodeResult.failure(InvalidMessageBodyFailure("Failure to parse multipart body", Some(t)))
            }
          }
          
          lazy val empty:DecodeResult[Multipart] = DecodeResult.failure(InvalidMessageBodyFailure("Nothing returned"))
          
          parseResult.fold(empty)(full)
      }
    )
}
