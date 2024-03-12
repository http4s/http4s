package org.http4s.multipart

import cats.arrow.FunctionK
import cats.effect.Sync
import cats.{ Applicative, ~> }
import fs2.Stream
import fs2.io.file.{ Files, Path }
import org.http4s.{ DecodeFailure, EntityBody, Headers, InvalidMessageBodyFailure }
import org.http4s.headers.`Content-Disposition`

trait MultipartReceiver[F[_], A] { self =>
  type Partial
  def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]]
  def assemble(partials: List[Partial]): Either[DecodeFailure, A]

  def ignoreUnexpectedParts: MultipartReceiver[F, A] =
    new MultipartReceiver.IgnoreUnexpected[F, A, Partial](this)

  def rejectUnexpectedParts: MultipartReceiver[F, A] =
    new MultipartReceiver.RejectUnexpected[F, A, Partial](this)

  def transformParts(f: PartReceiver[F, Partial] => PartReceiver[F, Partial]): MultipartReceiver[F, A] =
    new MultipartReceiver.TransformPart[F, A, Partial](this, f)
}

object MultipartReceiver {
  type Aux[F[_], A, P] = MultipartReceiver[F, A] {type Partial = P}

  def apply[F[_]] = new PartialApply[F]

  private def partName(headers: Headers) = headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"name"))
  private def partFilename(headers: Headers) = headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"filename"))
  private def fail(message: String) = InvalidMessageBodyFailure(message)

  sealed trait FieldValue {
    def rawValue[F[_] : Files]: EntityBody[F]
    def foldValue[A](onString: String => A, onFile: (String, Path) => A): A
  }
  object FieldValue {
    case class OfString(value: String) extends FieldValue {
      def rawValue[F[_] : Files] = fs2.text.utf8.encode[F](Stream.emit(value))
      def foldValue[A](onString: String => A, onFile: (String, Path) => A) = onString(value)
    }
    case class OfFile(filename: String, path: Path) extends FieldValue {
      def rawValue[F[_] : Files]: EntityBody[F] = Files[F].readAll(path)
      def foldValue[A](onString: String => A, onFile: (String, Path) => A): A = onFile(filename, path)
    }
  }

  class PartialApply[F[_]] {
    private def R = PartReceiver[F]
    private type IsFilePart = Boolean

    def anyPart(name: String) = new PartNamedPartialApply[cats.Id](name, _ => None, new ExpectOne(name))
    def filePart(name: String) = new PartNamedPartialApply[cats.Id](name, isFile => Option.when(!isFile) { fail(s"Expected file content in '$name' part") }, new ExpectOne(name))
    def textPart(name: String) = new PartNamedPartialApply[cats.Id](name, isFile => Option.when(isFile) { fail(s"Expected text content in '$name' part") }, new ExpectOne(name))

    def anyPartOpt(name: String) = new PartNamedPartialApply[Option](name, _ => None, new ExpectOpt(name))
    def filePartOpt(name: String) = new PartNamedPartialApply[Option](name, isFile => Option.when(!isFile) { fail(s"Expected file content in '$name' part") }, new ExpectOpt(name))
    def textPartOpt(name: String) = new PartNamedPartialApply[Option](name, isFile => Option.when(isFile) { fail(s"Expected text content in '$name' part") }, new ExpectOpt(name))

    def anyPartList(name: String) = new PartNamedPartialApply[List](name, _ => None, new ExpectList(name))
    def filePartList(name: String) = new PartNamedPartialApply[List](name, isFile => Option.when(!isFile) { fail(s"Expected file content in '$name' part") }, new ExpectList(name))
    def textPartList(name: String) = new PartNamedPartialApply[List](name, isFile => Option.when(isFile) { fail(s"Expected text content in '$name' part") }, new ExpectList(name))

    class PartNamedPartialApply[C[_]](
      name: String,
      restriction: IsFilePart => Option[DecodeFailure],
      assemble: List ~> Lambda[x => Either[DecodeFailure, C[x]]],
    ) {
      def apply[A](partReceiver: PartReceiver.PartialApply[F] => PartReceiver[F, A]): MultipartReceiver[F, C[A]] = {
        new SinglePartDecider[C, A](name, restriction, partReceiver, assemble)
      }
    }

    private class ExpectOne(name: String) extends FunctionK[List, Either[DecodeFailure, *]] {
      def apply[A](partials: List[A]): Either[DecodeFailure, A] = partials match {
        case Nil => Left(fail(s"Missing expected part: '$name'"))
        case one :: Nil => Right(one)
        case _ => Left(fail(s"Unexpected multiple parts found with name '$name'"))
      }
    }
    private class ExpectOpt(name: String) extends FunctionK[List, Lambda[x => Either[DecodeFailure, Option[x]]]] {
      def apply[A](partials: List[A]): Either[DecodeFailure, Option[A]] = partials match {
        case Nil => Right(None)
        case one :: Nil => Right(Some(one))
        case _ => Left(fail(s"Unexpected multiple parts found with name '$name'"))
      }
    }
    private class ExpectList(name: String) extends FunctionK[List, Lambda[x => Either[DecodeFailure, List[x]]]] {
      def apply[A](partials: List[A]): Either[DecodeFailure, List[A]] = Right(partials)
    }

    private class SinglePartDecider[C[_], A](
      name: String,
      restriction: IsFilePart => Option[DecodeFailure],
      partReceiver: PartReceiver.PartialApply[F] => PartReceiver[F, A],
      _assemble: List ~> Lambda[x => Either[DecodeFailure, C[x]]],
    ) extends MultipartReceiver[F, C[A]] {
      type Partial = A
      def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] = partName(partHeaders).filter(_ == name).map { _ =>
        val isFilePart: IsFilePart = partFilename(partHeaders).isDefined
        restriction(isFilePart) match {
          case Some(failure) => R.reject(failure)
          case None => partReceiver(R)
        }
      }
      def assemble(partials: List[A]): Either[DecodeFailure, C[A]] = _assemble(partials)
    }

    def auto(implicit F: Sync[F], files: Files[F]): MultipartReceiver[F, Map[String, FieldValue]] = new MultipartReceiver[F, Map[String, FieldValue]] {
      type Partial = (String, FieldValue)
      def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] = (partName(partHeaders), partFilename(partHeaders)) match {
        case (Some(name), Some(filename)) => Some(R.toTempFile.map { path => name -> FieldValue.OfFile(filename, path) })
        case (Some(name), None) => Some(R.readString.map { str => name -> FieldValue.OfString(str) })
        case _ => None
      }
      def assemble(partials: List[Partial]): Either[DecodeFailure, Map[String, FieldValue]] = Right(partials.toMap)
    }

  }

  private class IgnoreUnexpected[F[_], A, P](inner: Aux[F, A, P]) extends MultipartReceiver[F, A] {
    type Partial = Option[P]
    def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] = {
      Some(inner.decide(partHeaders).map(_.map[Partial](Some(_))).getOrElse {
        PartReceiver[F].ignore.map[Partial](_ => None)
      })
    }
    def assemble(partials: List[Partial]): Either[DecodeFailure, A] = {
      inner.assemble(partials.flatten)
    }
  }

  private class RejectUnexpected[F[_], A, P](inner: Aux[F, A, P]) extends MultipartReceiver[F, A] {
    type Partial = P
    def decide(partHeaders: Headers): Option[PartReceiver[F, P]] = inner.decide(partHeaders).orElse {
      Some(PartReceiver[F].reject[Partial] {
        partName(partHeaders) match {
          case None => InvalidMessageBodyFailure("Unexpected anonymous part")
          case Some(name) => InvalidMessageBodyFailure(s"Unexpected part: '$name'")
        }
      })
    }
    def assemble(partials: List[P]): Either[DecodeFailure, A] = inner.assemble(partials)
  }

  private class TransformPart[F[_], A, P](inner: Aux[F, A, P], f: PartReceiver[F, P] => PartReceiver[F, P]) extends MultipartReceiver[F, A] {
    type Partial = P
    def decide(partHeaders: Headers): Option[PartReceiver[F, P]] = inner.decide(partHeaders).map(f)
    def assemble(partials: List[P]): Either[DecodeFailure, A] = inner.assemble(partials)
  }

  implicit def multipartReceiverApplicative[F[_]]: Applicative[MultipartReceiver[F, *]] = new MultipartReceiverApplicative[F]

  class MultipartReceiverApplicative[F[_]] extends Applicative[MultipartReceiver[F, *]] {
    def pure[A](x: A): MultipartReceiver[F, A] = new MultipartReceiver[F, A] {
      type Partial = Unit
      def decide(partHeaders: Headers): Option[PartReceiver[F, Partial]] = None
      def assemble(partials: List[Partial]): Either[DecodeFailure, A] = Right(x)
    }
    def ap[A, B](ff: MultipartReceiver[F, A => B])(fa: MultipartReceiver[F, A]): MultipartReceiver[F, B] = new MultipartReceiver[F, B] {
      type Partial = Either[ff.Partial, fa.Partial]
      def decide(partHeaders: Headers): Option[PartReceiver[F, Either[ff.Partial, fa.Partial]]] = {
        // the order that the `orElse` operands appear decides the precedence in cases like `(receiver1, receiver2).mapN`
        // where both receivers could decide to consume the Part; only one is allowed, so we have to choose a winner.
        ff.decide(partHeaders).map(_.map(Left(_): Partial)) orElse fa.decide(partHeaders).map(_.map(Right(_): Partial))
      }
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