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

import cats._
import fs2.Chunk

sealed trait Http1Encoder[A] { self =>
  import Http1Encoder.Buffer

  protected def put(buf: Buffer, a: A): Unit

  def encode(a: A): Chunk[Byte] = {
    val buf = new Buffer()
    put(buf, a)
    buf.toChunk
  }

  def contramap[B](f: B => A) =
    new Http1Encoder[B] {
      override protected def put(buf: Buffer, b: B): Unit =
        self.put(buf, f(b))
      override def encode(b: B): Chunk[Byte] =
        self.encode(f(b))
    }

  def product[B](fb: Http1Encoder[B]): Http1Encoder[(A, B)] =
    new Http1Encoder[(A, B)] {
      override protected def put(buf: Buffer, ab: (A, B)): Unit = {
        self.put(buf, ab._1)
        fb.put(buf, ab._2)
      }
    }

  def prefix(fb: Http1Encoder[Unit]): Http1Encoder[A] =
    new Http1Encoder[A] {
      override protected def put(buf: Buffer, a: A): Unit = {
        fb.put(buf, ())
        self.put(buf, a)
      }
    }

  def suffix(fb: Http1Encoder[Unit]): Http1Encoder[A] =
    new Http1Encoder[A] {
      override protected def put(buf: Buffer, a: A): Unit = {
        self.put(buf, a)
        fb.put(buf, ())
      }
    }
}

object Http1Encoder {
  private[Http1Encoder] class Buffer() {
    private[this] var capacity = 256
    private[this] var buf = new Array[Byte](capacity)
    private[this] var pos = 0

    def +=(cs: CharSequence): Unit = {
      val len = cs.length()
      ensureCapacity(pos + len)
      var i = 0
      while (i < len) {
        buf(pos) = cs.charAt(i).toByte
        i += 1
        pos += 1
      }
    }

    def +=(b: Byte): Unit = {
      ensureCapacity(pos + 1)
      buf(pos) = b
      pos += 1
    }

    private[this] def ensureCapacity(n: Int) =
      if (buf.size < n) {
        val newCapacity = capacity * 2
        val newBuf = new Array[Byte](newCapacity)
        Array.copy(buf, 0, newBuf, 0, pos)
        buf = newBuf
      }

    def toChunk = Chunk.array(buf, 0, pos)
  }

  val ascii: Http1Encoder[CharSequence] =
    new Http1Encoder[CharSequence] {
      override protected def put(buf: Buffer, cs: CharSequence) =
        buf += cs
    }

  val byte: Http1Encoder[Byte] =
    new Http1Encoder[Byte] {
      override protected def put(buf: Buffer, b: Byte) =
        buf += b
    }

  val int: Http1Encoder[Int] =
    new Http1Encoder[Int] {
      override protected def put(buf: Buffer, i: Int) =
        buf += i.toString
    }

  def seq[A](fa: Http1Encoder[A]): Http1Encoder[Seq[A]] =
    new Http1Encoder[Seq[A]] {
      override protected def put(buf: Buffer, seq: Seq[A]) =
        seq.foreach { a =>
          fa.put(buf, a)
        }
    }

  def const[A](fa: Http1Encoder[A], a: A): Http1Encoder[Unit] =
    new Http1Encoder[Unit] {
      override protected def put(buf: Buffer, unit: Unit) = {
        val _ = unit
        fa.put(buf, a)
      }
    }

  implicit val ContravariantSemigroupalHttp1Encoder: ContravariantSemigroupal[Http1Encoder] =
    new ContravariantSemigroupal[Http1Encoder] {
      def contramap[A, B](fa: Http1Encoder[A])(f: B => A) =
        fa.contramap(f)
      def product[A, B](fa: Http1Encoder[A], fb: Http1Encoder[B]) =
        fa.product(fb)
    }
}
