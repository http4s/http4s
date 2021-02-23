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

package org.http4s

import fs2.{Stream, Chunk}
import cats.Monoid
import cats.syntax.all._

sealed trait Entity[F[_]]{
  def body: Stream[F, Byte]
  def length: Option[Long]
}

object Entity {

  case class Strict[F[_]](chunk: Chunk[Byte]) extends Entity[F]{
    def body: Stream[F, Byte] = Stream.chunk(chunk)
    def length: Option[Long] = chunk.size.toLong.some
  }

  case class TrustMe[F[_]](body: Stream[F, Byte], size: Long) extends Entity[F]{
    def length: Option[Long] = size.some
  }

  case class Chunked[F[_]](body: Stream[F, Byte]) extends Entity[F]{
    def length: Option[Long] = None
  }

  case class Empty[F[_]]() extends Entity[F]{
    def body: Stream[fs2.Pure,Byte] = Stream.empty
    def length: Option[Long] = Some(0L)
  }


  implicit def http4sMonoidForEntity[F[_]]: Monoid[Entity[F]] =
    new Monoid[Entity[F]] {
      def combine(a1: Entity[F], a2: Entity[F]): Entity[F] = (a1, a2) match {
        case (Empty(), other) => other
        case (other, Empty()) => other
        case (Chunked(body), a2) => Chunked(body ++ a2.body)
        case (a1, Chunked(body)) => Chunked(a1.body ++ body)
        case (Strict(c1), Strict(c2)) => Strict(c1 ++ c2)
        case (Strict(chunk), TrustMe(s2, size2)) => TrustMe(Stream.chunk(chunk) ++ s2, chunk.size.toLong + size2)
        case (TrustMe(s1, size1), Strict(chunk)) => TrustMe(s1 ++ Stream.chunk(chunk), size1 + chunk.size.toLong) 
        case (TrustMe(s1, size1), TrustMe(s2, size2)) => TrustMe(s1 ++ s2, size1 + size2)
      }
      val empty: Entity[F] =
        Entity.Empty()
    }
}
