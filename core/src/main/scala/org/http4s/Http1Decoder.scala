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
import cats.parse.Parser0
import java.nio.charset.StandardCharsets.US_ASCII
import fs2.Chunk

sealed trait Http1Decoder[A] { self =>
  def decode(chunk: Chunk[Byte]): Either[ParseFailure, (Int, A)]

  def product[B](fb: Http1Decoder[B]): Http1Decoder[(A, B)] =
    new Http1Decoder[(A, B)] {
      override def decode(chunk: Chunk[Byte]): Either[ParseFailure, (Int, (A, B))] =
        self.decode(chunk).flatMap { case (offsetA, a) =>
          fb.decode(chunk.drop(offsetA)).map { case (offsetB, b) =>
            (offsetB, (a, b))
          }
        }
    }

  def map[B](f: A => B): Http1Decoder[B] =
    new Http1Decoder[B] {
      def decode(chunk: Chunk[Byte]): Either[ParseFailure, (Int, B)] =
        self.decode(chunk).map { case (offset, a) =>
          (offset, f(a))
        }
    }
}

object Http1Decoder { self =>
  def catsParse[A](p: Parser0[A], errorMessage: => String): Http1Decoder[A] =
    new Http1Decoder[A] {
      def decode(chunk: Chunk[Byte]): Either[ParseFailure, (Int, A)] = {
        val s = new String(chunk.toArray, US_ASCII)
        try p.parse(s) match {
          case Left(e) =>
            Left(ParseFailure(errorMessage, e.toString))
          case Right((leftover, a)) =>
            Right((s.length - leftover.length, a))
        } catch {
          case p: ParseFailure => Left(p)
        }
      }
    }

  def pure[A](a: A): Http1Decoder[A] =
    new Http1Decoder[A] {
      def decode(chunk: Chunk[Byte]) = Right((0, a))
    }

  val unit: Http1Decoder[Unit] =
    new Http1Decoder[Unit] {
      def decode(chunk: Chunk[Byte]) = Right((0, ()))
    }

  implicit val catsInstancesForHttp4sHttp1Decoder: Applicative[Http1Decoder] =
    new Applicative[Http1Decoder] {
      override def map[A, B](fa: Http1Decoder[A])(f: A => B) =
        fa.map(f)
      def ap[A, B](ff: Http1Decoder[A => B])(fa: Http1Decoder[A]) =
        ff.product(fa).map { case (f, a) => f(a) }
      def pure[A](a: A): Http1Decoder[A] =
        self.pure(a)
      override def unit: Http1Decoder[Unit] = self.unit
    }
}
