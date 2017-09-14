package org.http4s.multipart

import cats.Eq
import cats.implicits._
import fs2.Chunk
import java.nio.charset.StandardCharsets
import scala.util.Random

final case class Boundary(value: String) extends AnyVal {
  def toChunk: Chunk[Byte] =
    Chunk.bytes(value.getBytes(StandardCharsets.UTF_8))
}

object Boundary {
  private val BoundaryLength = 40
  val CRLF = "\r\n"

  private val DIGIT = ('0' to '9').toList
  private val ALPHA = ('a' to 'z').toList ++ ('A' to 'Z').toList
  // ' ' and '?' are also allowed by spec, but mean we need to quote
  // the boundary in the media type, which causes some implementations
  // pain.
  private val OTHER = """'()+_,-./:=""".toSeq
  private val CHARS = (DIGIT ++ ALPHA ++ OTHER).toList
  private val nchars = CHARS.length
  private val rand = new Random()

  private def nextChar = CHARS(rand.nextInt(nchars - 1))
  private def stream: scala.Stream[Char] = scala.Stream.continually(nextChar)
  //Don't use filterNot it works for 2.11.4 and nothing else, it will hang.
  private def endChar: Char = stream.filter(_ != ' ').headOption.getOrElse('X')
  private def value(l: Int): String = stream.take(l).mkString

  def create: Boundary = Boundary(value(BoundaryLength) + endChar)

  implicit val boundaryEq: Eq[Boundary] = Eq.by(_.value)
}
