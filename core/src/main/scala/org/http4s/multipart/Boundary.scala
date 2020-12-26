/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.multipart

import cats.Eq
import fs2.Chunk
import java.nio.charset.StandardCharsets
import scala.util.Random
import scala.collection.compat.immutable.LazyList

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
  private def stream: LazyList[Char] =
    LazyList.continually(nextChar)
  //Don't use filterNot it works for 2.11.4 and nothing else, it will hang.
  private def endChar: Char = stream.filter(_ != ' ').headOption.getOrElse('X')
  private def value(l: Int): String = stream.take(l).mkString

  def create: Boundary = Boundary(value(BoundaryLength) + endChar)

  implicit val boundaryEq: Eq[Boundary] = Eq.by(_.value)
}
