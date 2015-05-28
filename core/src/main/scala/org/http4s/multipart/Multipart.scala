package org.http4s
package multipart

import scala.util.Random

import org.http4s._
import org.http4s.EntityEncoder._
import org.http4s.headers.{ `Content-Type` ⇒ ContentType, `Content-Disposition` => ContentDisposition }
import org.http4s.MediaType._

import scalaz._
import Scalaz._

import scodec.bits.ByteVector

final case class Name(value:String) extends AnyVal
sealed trait Part
final case class FormData(name:Name,
                          contentType: Option[ContentType],
                          content: Entity) extends Part

final case class Multipart(val parts: Seq[Part],val boundary:Boundary = B.oundary) {

  def headers = Headers(ContentType(MediaType.multipart("form-data", Some(boundary.value))))

}
case class Boundary(val value: String) {

  lazy val toBV = ByteVector(value.getBytes)

  lazy val lengthBV = toBV.length

}
object B {

  val CRLF = "\r\n"
  
  val CRLFBV = ByteVector(CRLF.getBytes)

  private val DIGIT = ('0' to '9').toList
  private val ALPHA = ('a' to 'z').toList ++ ('A' to 'Z').toList
  private val OTHER = '\'' :: '(' :: ')' :: '+' ::
                      '_' :: ',' :: '-' :: '.' ::
                      '/' :: ':' :: '=' :: '?' ::
                      ' ' :: Nil
  private val CHARS = (DIGIT ++ ALPHA ++ OTHER).toList
  private val nchars = CHARS.length
  private val rand = new Random()

  private def nextChar = CHARS(rand.nextInt(nchars - 1))
  private def stream: Stream[Char] = Stream continually (nextChar)
  //Don't use filterNot it works for 2.11.4 and nothing else, it will hang.
  private def endChar: Char = stream.filter(_ != ' ').headOption.getOrElse('X')
  private def value(l: Int): String = (stream take l).mkString

  def oundary = Boundary(value(rand.nextInt(69)) + endChar)

}

object MultipartHeaders {

  private val delimiter = ByteVector(":".getBytes)  
  
  def apply(bv:ByteVector): ParseFailure \/ Headers = { 
    val empty:ParseFailure \/ Headers = Headers.empty.right
    
    val op:(ParseFailure \/ Headers, ByteVector) => ParseFailure \/ Headers = { (acc,h) =>
      lazy val (name,value ) = h.splitAt(h.indexOfSlice(delimiter))
      lazy val header  = (name.decodeUtf8 |@| value.drop(1).decodeUtf8) { (n,v) =>  Header(n.trim(),v.trim()) }.
                          disjunction.
                          leftMap { t => ParseFailure("Unable to parse header", t.getMessage) }      
      acc.flatMap { c => header.fold( _.left , h => c.put(h).right) }
    }
    
    bv.toStream(Token(B.CRLF)).foldLeft(empty)(op)        
  }  
}
