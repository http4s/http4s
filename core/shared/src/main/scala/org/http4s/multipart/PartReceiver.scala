package org.http4s.multipart

import cats.effect.kernel.Resource
import cats.{ Applicative, ApplicativeError, MonadThrow }
import fs2.io.file.{ Files, Path }
import fs2.{ Compiler, Pipe, Pull, Pure, RaiseThrowable, Stream }
import org.http4s.{ DecodeFailure, EntityDecoder, Headers, InvalidMessageBodyFailure }

trait PartReceiver[F[_], A] {
  def receive(part: Part[F]): Resource[F, Either[DecodeFailure, A]]

  def map[B](f: A => B): PartReceiver[F, B] =
    part => this.receive(part).map(_.map(f))

  def mapWithHeaders[B](f: (Headers, A) => B): PartReceiver[F, B] =
    part => this.receive(part).map(_.map(f(part.headers, _)))

  def tapStart(onStart: F[Unit]): PartReceiver[F, A] =
    part => Resource.eval(onStart).flatMap(_ => this.receive(part))

  def tapResult(onResult: Either[DecodeFailure, A] => F[Unit]): PartReceiver[F, A] =
    part => this.receive(part).evalTap(onResult)

  def tapRelease(onRelease: F[Unit])(implicit F: Applicative[F]): PartReceiver[F, A] =
    part => Resource.make(F.unit)(_ => onRelease).flatMap(_ => this.receive(part))

  def preprocess(transformPartBody: Pipe[F, Byte, Byte]): PartReceiver[F, A] =
    part => this.receive(part.copy(body = transformPartBody(part.body)))

  def withSizeLimit(limit: Long)(implicit F: ApplicativeError[F, Throwable]): PartReceiver[F, A] =
    preprocess(PartReceiver.limitPartSize[F](limit))
}

object PartReceiver {

  def apply[F[_]] = new PartialApply[F]

  class PartialApply[F[_]] {
    def readString(implicit F: RaiseThrowable[F], cmp: Compiler[F, F]): PartReceiver[F, String] =
      part => Resource.eval(part.bodyText.compile.string).map(Right(_))

    def toTempFile(implicit F: Files[F], c: Compiler[F, F]): PartReceiver[F, Path] =
      part =>
        F.tempFile
          .evalTap(path => part.body.through(F.writeAll(path)).compile.drain)
          .map(Right(_))

    def ignore: PartReceiver[F, Unit] =
      _ => Resource.pure(Right(()))

    def reject[A](err: DecodeFailure): PartReceiver[F, A] =
      _ => Resource.pure(Left(err))

    def decode[A](implicit decoder: EntityDecoder[F, A]): PartReceiver[F, A] =
      part => Resource.eval(decoder.decode(part, strict = false).value)

    def decodeStrict[A](implicit decoder: EntityDecoder[F, A]): PartReceiver[F, A] =
      part => Resource.eval(decoder.decode(part, strict = true).value)

    def toMixedBuffer(maxSizeBeforeFile: Int)(implicit files: Files[F], mt: MonadThrow[F], c: Compiler[F, F]): PartReceiver[F, Stream[F, Byte]] =
      part => readToBuffer[F](part.body, maxSizeBeforeFile).map(Right(_))
  }

  private def limitPartSize[F[_]](
      maxPartSizeBytes: Long
  )(implicit F: ApplicativeError[F, Throwable]): Pipe[F, Byte, Byte] = {
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

  private def readToBuffer[F[_]: Files : MonadThrow](input: Stream[F, Byte], maxSizeBeforeFile: Int)(implicit c: Compiler[F, F]): Resource[F, Stream[F, Byte]] = {
    final case class Acc(bytes: Stream[Pure, Byte], bytesSize: Int)
    def go(acc: Acc, s: Stream[F, Byte]): Pull[F, Resource[F, Stream[F, Byte]], Unit] = s.pull.uncons.flatMap {
      case Some((headChunk, tail)) =>
        val newSize = acc.bytesSize + headChunk.size
        val newBytes = acc.bytes ++ Stream.chunk(headChunk)
        if (newSize > maxSizeBeforeFile) {
          // dump accumulated buffer to a temp file and continue
          // the pull, writing chunks to the file instead of accumulating
          val toDump = newBytes ++ tail
          Pull.output1(Files[F].tempFile.evalTap { path =>
            toDump.through(Files[F].writeAll(path)).compile.drain
          }.map(Files[F].readAll))
        } else {
          // add the incoming chunk to the in-memory buffer and recurse
          val newAcc = Acc(acc.bytes ++ Stream.chunk(headChunk), newSize)
          go(newAcc, tail)
        }

      case None =>
        println("buffer stayed in memory")
        Pull.output1(Resource.pure(acc.bytes.covary[F]))
    }
    Resource.suspend {
      go(Acc(Stream.empty, 0), input).stream.compile.lastOrError
    }
  }
}
