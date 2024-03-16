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

import cats.Applicative
import cats.effect.Sync
import cats.syntax.foldable.*
import fs2.io.file.Files
import org.http4s.DecodeFailure
import org.http4s.Headers
import org.http4s.InvalidMessageBodyFailure
import org.http4s.headers.`Content-Disposition`
import org.typelevel.ci.*

trait MultipartReceiver[F[_], A] { self =>
  type Partial
  def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]]
  def assemble(partials: List[Partial]): Either[DecodeFailure, A]

  def decideOrReject(partHeaders: Headers): PartReceiver[F, Partial] =
    decide(partHeaders).getOrElse {
      PartReceiver.reject[F, Partial](MultipartReceiver.partName(partHeaders) match {
        case None => InvalidMessageBodyFailure("Unexpected anonymous part")
        case Some(name) => InvalidMessageBodyFailure(s"Unexpected part: '$name'")
      })
    }

  def ignoreUnexpectedParts: MultipartReceiver[F, A] =
    new MultipartReceiver.IgnoreUnexpected[F, A, Partial](this)

  def transformParts(
      f: PartReceiver[F, Partial] => PartReceiver[F, Partial]
  ): MultipartReceiver[F, A] =
    new MultipartReceiver.TransformPart[F, A, Partial](this, f)
}

object MultipartReceiver {
  type Aux[F[_], A, P] = MultipartReceiver[F, A] { type Partial = P }

  private def partName(headers: Headers) =
    headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"name"))
  private def partFilename(headers: Headers) =
    headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"filename"))
  private def fail(message: String) = InvalidMessageBodyFailure(message)

  def at[F[_], A](name: String, receiver: PartReceiver[F, A]) = new At[F, A](name, receiver)

  final class At[F[_], A](name: String, receiver: PartReceiver[F, A]) {
    def once: MultipartReceiver[F, A] = new ReceiverAt(name, receiver)
      with MultipartReceiver[F, A] {
      def assemble(partials: List[A]): Either[DecodeFailure, A] = partials match {
        case Nil => Left(fail(s"Missing expected part: '$name'"))
        case one :: Nil => Right(one)
        case _ => Left(fail(s"Unexpected multiple parts found with name '$name'"))
      }
    }

    def asOption: MultipartReceiver[F, Option[A]] = new ReceiverAt(name, receiver)
      with MultipartReceiver[F, Option[A]] {
      def assemble(partials: List[A]): Either[DecodeFailure, Option[A]] = partials match {
        case Nil => Right(None)
        case one :: Nil => Right(Some(one))
        case _ => Left(fail(s"Unexpected multiple parts found with name '$name'"))
      }
    }

    def asList: MultipartReceiver[F, List[A]] = new ReceiverAt(name, receiver)
      with MultipartReceiver[F, List[A]] {
      def assemble(partials: List[Partial]): Either[DecodeFailure, List[A]] = Right(partials)
    }
  }

  private abstract class ReceiverAt[F[_], A](name: String, receiver: PartReceiver[F, A]) {
    type Partial = A
    def decide(headers: Headers): Option[PartReceiver[F, A]] =
      if (partName(headers).contains(name)) Some(receiver)
      else None
  }

  def auto[F[_]: Sync: Files]: MultipartReceiver[F, Map[String, PartValue]] =
    new MultipartReceiver[F, Map[String, PartValue]] {
      type Partial = (String, PartValue)
      def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] =
        (partName(partHeaders), partFilename(partHeaders)) match {
          case (Some(name), Some(filename)) =>
            Some(PartReceiver.toTempFile[F].map { path =>
              name -> PartValue.OfFile(filename, path)
            })
          case (Some(name), None) =>
            Some(PartReceiver.bodyText[F].map(str => name -> PartValue.OfString(str)))
          case _ => None
        }
      def assemble(partials: List[Partial]): Either[DecodeFailure, Map[String, PartValue]] = Right(
        partials.toMap
      )
    }

  def uniform[F[_], A](partReceiver: PartReceiver[F, A]): MultipartReceiver.Aux[F, List[A], A] =
    new MultipartReceiver[F, List[A]] {
      type Partial = A
      def decide(partHeaders: Headers) = Some(partReceiver)
      def assemble(partials: List[Partial]) = Right(partials)
    }

  private class IgnoreUnexpected[F[_], A, P](inner: Aux[F, A, P]) extends MultipartReceiver[F, A] {
    type Partial = Option[P]
    def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] =
      Some(inner.decide(partHeaders).map(_.map[Partial](Some(_))).getOrElse {
        PartReceiver.ignore[F].map[Partial](_ => None)
      })
    def assemble(partials: List[Partial]): Either[DecodeFailure, A] =
      inner.assemble(partials.flatten)
  }

  private class TransformPart[F[_], A, P](
      inner: Aux[F, A, P],
      f: PartReceiver[F, P] => PartReceiver[F, P],
  ) extends MultipartReceiver[F, A] {
    type Partial = P
    def decide(partHeaders: Headers): Option[PartReceiver[F, P]] = inner.decide(partHeaders).map(f)
    def assemble(partials: List[P]): Either[DecodeFailure, A] = inner.assemble(partials)
  }

  implicit def multipartReceiverApplicative[F[_]]: Applicative[MultipartReceiver[F, *]] =
    new MultipartReceiverApplicative[F]

  private class MultipartReceiverApplicative[F[_]] extends Applicative[MultipartReceiver[F, *]] {
    def pure[A](x: A): MultipartReceiver[F, A] = new MultipartReceiver[F, A] {
      type Partial = Unit
      def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] = None
      def assemble(partials: List[Partial]): Either[DecodeFailure, A] = Right(x)
    }
    def ap[A, B](
        ff: MultipartReceiver[F, A => B]
    )(fa: MultipartReceiver[F, A]): MultipartReceiver[F, B] = new MultipartReceiver[F, B] {
      type Partial = Either[ff.Partial, fa.Partial]
      def decide(partHeaders: Headers): Option[PartReceiver[F, Either[ff.Partial, fa.Partial]]] =
        // The order that the `orElse` operands appear decides the precedence in cases like `(receiver1, receiver2).mapN`
        // where both receivers could decide to consume the Part; only one is allowed, so we have to choose a winner.
        // This matters when combining a receiver for a specific field with a receiver that generically accepts all fields.
        ff.decide(partHeaders)
          .map(_.map(Left(_): Partial))
          .orElse(fa.decide(partHeaders).map(_.map(Right(_): Partial)))
      def assemble(partials: List[Either[ff.Partial, fa.Partial]]): Either[DecodeFailure, B] = {
        val (ffs, fas) = partials.partitionEither(identity)
        for {
          f <- ff.assemble(ffs)
          a <- fa.assemble(fas)
        } yield f(a)
      }
    }
  }
}
