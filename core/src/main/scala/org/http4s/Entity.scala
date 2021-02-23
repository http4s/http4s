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

import fs2.{Chunk, Stream}
import cats._
import cats.syntax.all._

sealed trait Entity[F[_]] {
  def body: Stream[F, Byte]
  def length: Option[Long]
  def translate[G[_]](fk: F ~> G): Entity[G]
  def covary[G[x] >: F[x]]: Entity[G] = translate[G](cats.arrow.FunctionK.id[F].widen[G])
}

object Entity {

  case class Strict[F[_]](chunk: Chunk[Byte]) extends Entity[F] {
    def body: Stream[F, Byte] = Stream.chunk(chunk)
    def length: Option[Long] = chunk.size.toLong.some
    def translate[G[_]](fk: F ~> G): Entity[G] = this.asInstanceOf[Strict[G]]
  }

  case class TrustMe[F[_]](body: Stream[F, Byte], size: Long) extends Entity[F] {
    def length: Option[Long] = size.some
    def translate[G[_]](fk: F ~> G): Entity[G] = TrustMe(body.translate(fk), size)
  }

  case class Chunked[F[_]](body: Stream[F, Byte]) extends Entity[F] {
    def length: Option[Long] = None
    def translate[G[_]](fk: F ~> G): Entity[G] = Chunked(body.translate(fk))
  }

  case class Empty[F[_]]() extends Entity[F] {
    def body: Stream[fs2.Pure, Byte] = Stream.empty
    def length: Option[Long] = Some(0L)
    def translate[G[_]](fk: F ~> G): Entity[G] = empty[G]
  }

  def strict[F[_]](chunk: Chunk[Byte]): Entity[F] = Strict(chunk)
  def trustMe[F[_]](body: Stream[F, Byte], size: Long): Entity[F] = TrustMe(body, size)
  def chunked[F[_]](body: Stream[F, Byte]): Entity[F] = Chunked(body)
  private val internalEmpty = Empty[fs2.Pure]()
  def empty[F[_]]: Entity[F] = internalEmpty.asInstanceOf[Empty[F]]

  implicit def encoder[F[_]]: EntityEncoder[F, Entity[F]] = EntityEncoder.encodeBy()(identity)

  implicit def http4sMonoidForEntity[F[_]]: Monoid[Entity[F]] =
    new Monoid[Entity[F]] {
      def combine(a1: Entity[F], a2: Entity[F]): Entity[F] = (a1, a2) match {
        case (Empty(), other) => other
        case (other, Empty()) => other
        case (Chunked(body), a2) => Chunked(body ++ a2.body)
        case (a1, Chunked(body)) => Chunked(a1.body ++ body)
        case (Strict(c1), Strict(c2)) => Strict(c1 ++ c2)
        case (Strict(chunk), TrustMe(s2, size2)) =>
          TrustMe(Stream.chunk(chunk) ++ s2, chunk.size.toLong + size2)
        case (TrustMe(s1, size1), Strict(chunk)) =>
          TrustMe(s1 ++ Stream.chunk(chunk), size1 + chunk.size.toLong)
        case (TrustMe(s1, size1), TrustMe(s2, size2)) => TrustMe(s1 ++ s2, size1 + size2)
      }
      val empty: Entity[F] =
        Entity.Empty()
    }
}
