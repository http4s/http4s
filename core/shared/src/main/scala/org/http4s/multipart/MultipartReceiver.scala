package org.http4s.multipart

import cats.arrow.FunctionK
import cats.effect.Sync
import cats.syntax.foldable.*
import cats.{Applicative, ~>}
import fs2.io.file.Files
import org.http4s.headers.`Content-Disposition`
import org.http4s.{DecodeFailure, Headers, InvalidMessageBodyFailure}
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

  private type IsFilePart = Boolean

  def anyPart(name: String) =
    new PartNamedPartialApply[cats.Id](name, _ => None, new ExpectOne(name))

  def filePart(name: String) = new PartNamedPartialApply[cats.Id](
    name,
    isFile => Option.when(!isFile)(fail(s"Expected file content in '$name' part")),
    new ExpectOne(name),
  )

  def textPart(name: String) = new PartNamedPartialApply[cats.Id](
    name,
    isFile => Option.when(isFile)(fail(s"Expected text content in '$name' part")),
    new ExpectOne(name),
  )

  def anyPartOpt(name: String) =
    new PartNamedPartialApply[Option](name, _ => None, new ExpectOpt(name))

  def filePartOpt(name: String) = new PartNamedPartialApply[Option](
    name,
    isFile => Option.when(!isFile)(fail(s"Expected file content in '$name' part")),
    new ExpectOpt(name),
  )

  def textPartOpt(name: String) = new PartNamedPartialApply[Option](
    name,
    isFile => Option.when(isFile)(fail(s"Expected text content in '$name' part")),
    new ExpectOpt(name),
  )

  def anyPartList(name: String) = new PartNamedPartialApply[List](name, _ => None, ExpectList)

  def filePartList(name: String) = new PartNamedPartialApply[List](
    name,
    isFile => Option.when(!isFile)(fail(s"Expected file content in '$name' part")),
    ExpectList,
  )

  def textPartList(name: String) = new PartNamedPartialApply[List](
    name,
    isFile => Option.when(isFile)(fail(s"Expected text content in '$name' part")),
    ExpectList,
  )

  class PartNamedPartialApply[C[_]](
      name: String,
      restriction: IsFilePart => Option[DecodeFailure],
      assemble: List ~> Lambda[x => Either[DecodeFailure, C[x]]],
  ) {
    def apply[F[_], A](partReceiver: PartReceiver[F, A]): MultipartReceiver[F, C[A]] =
      new SinglePartDecider[F, C, A](name, restriction, partReceiver, assemble)
  }

  private class ExpectOne(name: String) extends FunctionK[List, Either[DecodeFailure, *]] {
    def apply[A](partials: List[A]): Either[DecodeFailure, A] = partials match {
      case Nil => Left(fail(s"Missing expected part: '$name'"))
      case one :: Nil => Right(one)
      case _ => Left(fail(s"Unexpected multiple parts found with name '$name'"))
    }
  }

  private class ExpectOpt(name: String)
      extends FunctionK[List, Lambda[x => Either[DecodeFailure, Option[x]]]] {
    def apply[A](partials: List[A]): Either[DecodeFailure, Option[A]] = partials match {
      case Nil => Right(None)
      case one :: Nil => Right(Some(one))
      case _ => Left(fail(s"Unexpected multiple parts found with name '$name'"))
    }
  }

  private object ExpectList extends FunctionK[List, Lambda[x => Either[DecodeFailure, List[x]]]] {
    def apply[A](partials: List[A]): Either[DecodeFailure, List[A]] = Right(partials)
  }

  private class SinglePartDecider[F[_], C[_], A](
      name: String,
      restriction: IsFilePart => Option[DecodeFailure],
      partReceiver: PartReceiver[F, A],
      _assemble: List ~> Lambda[x => Either[DecodeFailure, C[x]]],
  ) extends MultipartReceiver[F, C[A]] {
    type Partial = A
    def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] =
      partName(partHeaders).filter(_ == name).map { _ =>
        val isFilePart: IsFilePart = partFilename(partHeaders).isDefined
        restriction(isFilePart) match {
          case Some(failure) => PartReceiver.reject(failure)
          case None => partReceiver
        }
      }
    def assemble(partials: List[A]): Either[DecodeFailure, C[A]] = _assemble(partials)
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

  def uniform[F[_], A, C[_]](
      partReceiver: PartReceiver[F, A]
  ): MultipartReceiver.Aux[F, List[A], A] =
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
        // the order that the `orElse` operands appear decides the precedence in cases like `(receiver1, receiver2).mapN`
        // where both receivers could decide to consume the Part; only one is allowed, so we have to choose a winner.
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
