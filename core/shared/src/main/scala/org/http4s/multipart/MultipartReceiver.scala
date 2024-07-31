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
import cats.Functor
import cats.effect.Concurrent
import cats.syntax.foldable._
import fs2.io.file.Files
import org.http4s.DecodeFailure
import org.http4s.Headers
import org.http4s.InvalidMessageBodyFailure

/** Composable logic for receiving data from `multipart/form-data` request bodies.
  *
  * A `MultipartReceiver` works by delegating to a `PartReceiver` for each `Part`
  * in the body. It may or may not use the same `PartReceiver` for each `Part`.
  * The results from each `Part` are collected to a `List[Partial]` (where `Partial`
  * is an abstract type significant to the specific `MultipartReceiver` instance),
  * then post-processed into a final result of type `A`.
  *
  * For the simplest use-cases, [[MultipartReceiver.auto]] will suffice. However,
  * `MultipartReceiver` is designed for customizability and control over how each
  * `Part` is handled as its data is received.
  *
  * `MultipartReceiver` is `Applicative` in type `F`, allowing receiver logic to
  * be defined for one field at a time, then compose the logic for each field into
  * a single receiver. You can define single-field receivers with the DSL starting
  * at [[MultipartReceiver.at]]. For example:
  *
  * {{{
  * case class Nominee(name: String, age: Int, accolades: List[String], photo: fs2.io.file.Path)
  *
  * val nomineeReceiver: MultipartReceiver[IO, Nominee] = (
  *   MultipartReceiver.at("name", PartReceiver.bodyText[IO].withSizeLimit(256)).once,
  *   MultipartReceiver.at("age", PartReceiver.decode[IO, Int].withSizeLimit(8)).once,
  *   MultipartReceiver.at("accolades", PartReceiver.bodyText[IO].withSizeLimit(1024)).asList,
  *   MultipartReceiver.at("photo", PartReceiver.toTempFile[IO].withSizeLimit(2 * 1024 * 1024)).once,
  * ).mapN(Nominee.apply)
  *
  * val nomineeDecoderRes: Resource[IO, EntityDecoder[IO, Nominee]] =
  *   MultipartDecoder.fromReceiver(nomineeReceiver)
  * }}}
  *
  * When used to construct an `EntityDecoder` via `MultipartDecoder.fromReceiver`, the
  * result is expressed as a `Resource`. This allows the `MultipartReceiver` to allocate
  * resources (like temp files) whose lifetimes are tied to the decoder resource. I.e.
  * {{{
  * nomineeDecoderRes.use { nomineeDecoder =>
  *   nomineeDecoder
  *     .decode(request, strict = false) // <- may allocate temp files
  * } // <- temp files deleted on release
  * }}}
  *
  * @tparam F The effect type
  * @tparam A The result type
  */
trait MultipartReceiver[F[_], A] { self =>

  /** Common supertype for the values decoded from each received part.
    */
  type Partial

  /** Upon encountering the Headers for a new part, decide how (if at all)
    * to handle the body of that part.
    *
    * Return  a `PartReceiver` wrapped in `Some` to use that receiver to
    * decode the body of the part.
    *
    * Return `None` to indicate the part is unexpected. By default, this will
    * result in a decode error upon encountering an unexpected part, but by
    * calling [[ignoreUnexpectedParts]] you can create a MultipartReceiver that
    * instead discards the body of any unexpected parts, without raising an error.
    *
    * @param partHeaders The `Headers` for a `Part` in a `multipart-form-data` request
    * @return The receiver logic for that part
    */
  def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]]

  /** At the end of the decoding process, assemble a final result from the individual
    * part-decode results that were returned by the `decide` method for each part.
    *
    * @param partials A list of decoded results as returned by the `decide` method for each part
    * @return `Right` to indicate a successful result, or `Left` if something went wrong
    */
  def assemble(partials: List[Partial]): Either[DecodeFailure, A]

  def map[B](f: A => B): MultipartReceiver[F, B] = Functor[MultipartReceiver[F, *]].map(this)(f)

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

  private def partName(headers: Headers) = Part(headers, fs2.Stream.empty).name
  private def partFilename(headers: Headers) = Part(headers, fs2.Stream.empty).filename
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

  /** Creates a `MultipartReceiver` which inspects each `Part`'s headers to decide whether
    * to receive each `Part` as text or as a file upload.
    *
    * Parts that look like file uploads will have their content written to a temp file,
    * represented with `PartValue.OfFile`. All other parts will be decoded to text,
    * represented with `PartValue.OfString`. Each part's name is used as a key in the
    * resulting `Map[String, PartValue]`.
    *
    * @tparam F The effect type
    * @return A `MultipartReceiver` which builds a `Map` containing each `Part`, where the
    *         map's key is the part name, and value is the decoded part, represented as
    *         either plain text or as a temporary file.
    */
  def auto[F[_]: Concurrent: Files]: MultipartReceiver[F, Map[String, PartValue]] =
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

  /** Creates a `MultipartReceiver` which handles all parts the same way, using the
    * given `partReceiver`.
    *
    * Note that the resulting `MultipartReceiver` does not inherently capture any metadata
    * about each `Part`; only the decoded values are returned as a `List`. The given
    * `partReceiver` can be constructed to provide the necessary metadata, e.g. by
    * using [[PartReceiver.withPartName]]
    *
    * Example usage:
    * {{{
    * val genericReceiver: MultipartReceiver[IO, Map[String, Stream[IO, Byte]]] =
    *   MultipartReceiver.uniform(
    *     PartReceiver
    *       .toMixedBuffer[IO](maxSizeBeforeFile = 10 * 1024 * 1024)
    *       .withPartName
    *   ).map(_.toMap)
    * }}}
    *
    * The above passes a `PartReceiver.toMixedBuffer` to load part data into memory
    * up to a certain per-part limit, dumping the buffer to a temporary file after
    * that limit is reached. The buffer for each part is exposed as a generic byte
    * stream which can safely be re-read until the underlying buffers are released.
    * The `withPartName` method transforms the `PartReceiver` to include the Part
    * name so we can return a convenient `Map` representation of the result.
    *
    * @param partReceiver The `PartReceiver` to use for each part
    * @tparam F The effect type
    * @tparam A The value type returned by the `partReceiver`
    * @return A `MultipartReceiver` which delegates to the given `partReceiver` for all parts,
    *         returning a `List` of all parts' decoded values.
    */
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
