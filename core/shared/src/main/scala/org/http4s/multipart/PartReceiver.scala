package org.http4s.multipart

import cats.{ Applicative, ApplicativeError }
import cats.effect.kernel.Resource
import fs2.{ Compiler, Pipe, Pull, RaiseThrowable, Stream }
import fs2.io.file.{ Files, Path }
import org.http4s.{ DecodeFailure, EntityDecoder, InvalidMessageBodyFailure }

trait PartReceiver[F[_], A] {
  def receive(part: Part[F]): Resource[F, Either[DecodeFailure, A]]

  def map[B](f: A => B): PartReceiver[F, B] = part => this.receive(part).map(_.map(f))

  def tapStart(onStart: F[Unit]): PartReceiver[F, A] =
    part => Resource.eval(onStart).flatMap { _ => this.receive(part) }

  def tapResult(onResult: Either[DecodeFailure, A] => F[Unit]): PartReceiver[F, A] =
    part => this.receive(part).evalTap(onResult)

  def tapRelease(onRelease: F[Unit])(implicit F: Applicative[F]): PartReceiver[F, A] =
    part => Resource.make(F.unit)(_ => onRelease).flatMap { _ => this.receive(part) }

  def preprocess(transformPartBody: Pipe[F, Byte, Byte]): PartReceiver[F, A] =
    part => this.receive(part.copy(body = transformPartBody(part.body)))

  def withSizeLimit(limit: Long)(implicit F: ApplicativeError[F, Throwable]): PartReceiver[F, A] =
    preprocess(PartReceiver.limitPartSize[F](limit))
}

object PartReceiver {

  def apply[F[_]] = new PartialApply[F]

  class PartialApply[F[_]] {
    def readString(implicit F: RaiseThrowable[F], cmp: Compiler[F, F]): PartReceiver[F, String] =
      part => Resource.eval { part.bodyText.compile.string }.map(Right(_))

    def toTempFile(implicit F: Files[F], c: Compiler[F, F]): PartReceiver[F, Path] =
      part => F.tempFile.evalTap { path => part.body.through(F.writeAll(path)).compile.drain }.map(Right(_))

    def ignore: PartReceiver[F, Unit] =
      part => Resource.pure(Right(()))

    def reject[A](err: DecodeFailure): PartReceiver[F, A] =
      part => Resource.pure(Left(err))

    def decode[A](implicit decoder: EntityDecoder[F, A]): PartReceiver[F, A] =
      part => Resource.eval(decoder.decode(part, strict = false).value)

    def decodeStrict[A](implicit decoder: EntityDecoder[F, A]): PartReceiver[F, A] =
      part => Resource.eval(decoder.decode(part, strict = true).value)

  }

  private def limitPartSize[F[_]](maxPartSizeBytes: Long)(implicit F: ApplicativeError[F, Throwable]): Pipe[F, Byte, Byte] = {
    def go(s: Stream[F, Byte], accumSize: Long): Pull[F, Byte, Unit] = s.pull.uncons.flatMap {
      case Some((chunk, tail)) =>
        val newSize = accumSize + chunk.size
        if (newSize <= maxPartSizeBytes) Pull.output(chunk) >> go(tail, newSize)
        else Pull.raiseError[F](InvalidMessageBodyFailure("Part body exceeds maximum length"))
      case None =>
        Pull.done
    }

    go(_, 0L).stream
  }
}