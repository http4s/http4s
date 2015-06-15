package org.http4s
package multipart

import scala.util.{Try, Success, Failure}
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
import org.parboiled2._

object MultipartEntityDecoder {

  private val boundary: Message => Option[Boundary] = msg => msg.contentType.flatMap{ _.mediaType.extensions.get("boundary").map( Boundary) }

  val decoder:EntityDecoder[Multipart] =  EntityDecoder.decodeBy(MediaRange.`multipart/*`)( msg =>
      boundary(msg) match {
        case None           =>  DecodeResult.failure(ParseFailure("Missing boundary extention to Content-Type. " ,
                                                msg.contentType.map(_.toString).getOrElse("No Content-Type found.")))
        case Some(boundary) =>  
          val parseResult:Option[Try[Seq[ParseFailure] \/ Multipart]] = msg.body.map { body => new MultipartParser(body.toArray, boundary).MultipartBody.run()}.runLast.run
          
          val full:Try[Seq[ParseFailure] \/ Multipart] => DecodeResult[Multipart] = { tri =>
            tri match {
              case Success(\/-(mp))  =>  DecodeResult.success(mp)
              case Success(-\/(pfs)) => 
                val failure = pfs.fold(ParseFailure("",""))( (acc,pf) => acc.copy( sanitized = acc.sanitized + "\n" + pf.sanitized , details = acc.details + "\n" + pf.details ) )
                DecodeResult.failure(failure)
              case Failure(t)        =>  DecodeResult.failure(ParseFailure(t.getMessage))
            }
          } 
          
          val empty:DecodeResult[Multipart] = DecodeResult.failure(ParseFailure("Parse failed, nothing returned "))
          
          
          parseResult.fold(empty)(full)

      }
    )

}
