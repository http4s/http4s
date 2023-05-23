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

import cats.Monoid
import cats.syntax.all._
import cats.~>
import fs2.Chunk
import fs2.Pure
import fs2.Stream
import scodec.bits.ByteVector

sealed trait Entity[+F[_]] {
  def body: EntityBody[F]
  def length: Option[Long]
  def ++[F1[x] >: F[x]](that: Entity[F1]): Entity[F1]
  def translate[F1[x] >: F[x], G[_]](fk: F1 ~> G): Entity[G]
}

object Entity {

  @deprecated("Use Entity.stream instead", "1.0.0-M38")
  def apply[F[_]](body: Stream[F, Byte], length: Option[Long] = None): Entity[F] =
    stream(body, length)

  def stream[F[_]](body: Stream[F, Byte], length: Option[Long] = None): Entity[F] =
    Streamed(body, length)

  // The type parameter aids type inference in Message constructors.
  def empty[F[_]]: Entity[F] = Empty
  def strict(bytes: ByteVector): Entity[Pure] = Strict(bytes)

  /** Generates a Strict entity with the string encoded the given charset. */
  def string(str: String, charset: Charset): Entity[Pure] =
    Entity.Strict(ByteVector(str.getBytes(charset.nioCharset)))

  /** Generates a Strict entity with the string encoded the given charset. */
  def utf8String(str: String): Entity[Pure] =
    string(str, Charset.`UTF-8`)

  final case class Streamed[+F[_]](body: EntityBody[F], length: Option[Long]) extends Entity[F] {
    def ++[F1[x] >: F[x]](that: Entity[F1]): Entity[F1] = that match {
      case d: Streamed[F1] => Streamed(body ++ d.body, (length, d.length).mapN(_ + _))
      case strict @ Strict(bytes) => Streamed(body ++ strict.body, length.map(_ + bytes.size))
      case Empty => this
    }

    def translate[F1[x] >: F[x], G[_]](fk: F1 ~> G): Entity[G] =
      Streamed(body.translate(fk), length)

    override def toString: String = length match {
      case None =>
        "Entity.Streamed"

      case Some(dataSize) =>
        s"Entity.Streamed($dataSize bytes total)"
    }
  }

  final case class Strict(bytes: ByteVector) extends Entity[Pure] {
    def body: EntityBody[Pure] = Stream.chunk(Chunk.byteVector(bytes))

    val length: Option[Long] = Some(bytes.size)

    def ++[F1[x] >: Pure[x]](that: Entity[F1]): Entity[F1] = that match {
      case d: Streamed[F1] => Streamed(body ++ d.body, d.length.map(bytes.size + _))
      case Strict(bytes2) => Strict(bytes ++ bytes2)
      case Empty => this
    }

    def translate[F1[x] >: Pure[x], G[_]](fk: F1 ~> G): Entity[G] = this

    override def toString: String =
      s"Entity.Strict(${bytes.size} bytes total)"
  }

  case object Empty extends Entity[Pure] {
    val body: EntityBody[Pure] = Stream.empty

    val length: Option[Long] = Some(0L)

    def ++[F1[x] >: Pure[x]](that: Entity[F1]): Entity[F1] = that

    def translate[F1[x] >: Pure[x], G[_]](fk: F1 ~> G): Entity[G] = this

    override def toString: String = "Entity.Empty"
  }

  implicit def http4sMonoidForEntity[F[_]]: Monoid[Entity[F]] =
    new Monoid[Entity[F]] {
      def combine(a1: Entity[F], a2: Entity[F]): Entity[F] = a1 ++ a2
      val empty: Entity[F] = Entity.empty
    }
}
