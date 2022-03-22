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
import cats.effect.Sync
import fs2.Chunk
import org.http4s.internal.CollectionCompat

import java.nio.charset.StandardCharsets
import scala.util.Random

final case class Boundary(value: String) extends AnyVal {
  def toChunk: Chunk[Byte] =
    Chunk.bytes(value.getBytes(StandardCharsets.UTF_8))
}

object Boundary {
  private val BoundaryLength = 41
  val CRLF = "\r\n"

  private val alphabet = {
    val arr = Array.newBuilder[Char]
    arr ++= ('0' to '9')
    arr ++= ('a' to 'z')
    arr ++= ('A' to 'Z')
    // Many more chars are allowed (see bchars definition in
    // https://www.rfc-editor.org/rfc/rfc2046), but these are known to
    // be robust in implementation.  Specifically, they don't trigger
    // quotation in the Content-Type parameter.
    arr += '-'
    arr += '_'
    arr.result()
  }
  private val nchars = alphabet.length
  private val defaultRandom = new Random()

  private def nextChar(random: Random) = alphabet(random.nextInt(nchars - 1))
  private def stream(random: Random): CollectionCompat.LazyList[Char] =
    CollectionCompat.LazyList.continually(nextChar(random))
  private def value(random: Random, l: Int): String =
    stream(random).take(l).mkString

  @deprecated("Impure. Use fromScalaRandom", "0.22.14")
  def create: Boundary = unsafeCreate()

  /** Create a new MIME boundary. */
  def fromScalaRandom[F[_]](random: Random)(implicit F: Sync[F]): F[Boundary] =
    F.delay(unsafeCreateFromScalaRandom(random))

  private[multipart] def unsafeCreate(): Boundary =
    unsafeCreateFromScalaRandom(defaultRandom)

  private def unsafeCreateFromScalaRandom(random: Random): Boundary =
    Boundary(value(random, BoundaryLength))

  implicit val boundaryEq: Eq[Boundary] = Eq.by(_.value)
}
