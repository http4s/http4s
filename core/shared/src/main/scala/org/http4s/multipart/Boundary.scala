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
import java.util.Base64
import scala.util.Random

final case class Boundary(value: String) extends AnyVal {
  def toChunk: Chunk[Byte] =
    Chunk.array(value.getBytes(StandardCharsets.UTF_8))
}

object Boundary {
  val CRLF = "\r\n"

  private val defaultRandom = new Random()

  @deprecated("Impure. Use Multiparts.boundary", "0.23.12")
  def create: Boundary = unsafeCreate()

  private[multipart] def unsafeCreate(): Boundary = {
    val bytes = new Array[Byte](30)
    defaultRandom.nextBytes(bytes)
    unsafeFromBytes(bytes)
  }

  private[this] val encoder = Base64.getUrlEncoder.withoutPadding

  private[multipart] def unsafeFromBytes(bytes: Array[Byte]): Boundary =
    Boundary(encoder.encodeToString(bytes))

  implicit val boundaryEq: Eq[Boundary] = Eq.by(_.value)
}
