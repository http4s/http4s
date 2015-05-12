package org.http4s
package multipart

import org.http4s._
import org.http4s.EntityEncoder._
import org.http4s.headers.{ `Content-Type` ⇒ ContentType, `Content-Disposition` => ContentDisposition }
import org.http4s.MediaType._

import scala.util.Random

import scodec.bits.ByteVector


final case class Name(value:String) extends AnyVal
sealed trait Part
final case class FormData(name:Name,
                          contentType: Option[ContentType],
                          content: Entity) extends Part

final case class MultiPart(val parts: Seq[Part],val boundary:Boundary = B.oundary) {

  def headers = Headers(ContentType(MediaType.multipart("form-data", Some(boundary.value))))

}
case class Boundary(val value: String) {

  lazy val toBV = ByteVector(value.getBytes)

  lazy val lengthBV = toBV.length

}
object B {

  val CRLF = "\r\n"

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
  //Don't use filterNot it works for 2.11.4 and nothing else, will hang.
  private def endChar: Char = stream.filter(_ != ' ').headOption.getOrElse('X')
  private def value(l: Int): String = (stream take l).mkString

  def oundary = Boundary(value(rand.nextInt(69)) + endChar)

}
object MultiPartDefinition {

  val dash = "--"



}
