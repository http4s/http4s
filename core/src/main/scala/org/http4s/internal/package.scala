package org.http4s

import java.util.concurrent.{
  CancellationException,
  CompletableFuture,
  CompletionException,
  CompletionStage
}

import cats.effect.implicits._
import cats.effect.{Async, Concurrent, ConcurrentEffect, ContextShift, Effect, IO}
import cats.implicits._
import org.http4s.util.execution.direct
import org.log4s.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}

package object internal {
  // Like fs2.async.unsafeRunAsync before 1.0.  Convenient for when we
  // have an ExecutionContext but not a Timer.
  private[http4s] def unsafeRunAsync[F[_], A](fa: F[A])(
      f: Either[Throwable, A] => IO[Unit])(implicit F: Effect[F], ec: ExecutionContext): Unit =
    F.runAsync(Async.shift(ec) *> fa)(f).unsafeRunSync

  private[http4s] def loggingAsyncCallback[A](logger: Logger)(
      attempt: Either[Throwable, A]): IO[Unit] =
    attempt match {
      case Left(e) => IO(logger.error(e)("Error in asynchronous callback"))
      case Right(_) => IO.unit
    }

  // Inspired by https://github.com/functional-streams-for-scala/fs2/blob/14d20f6f259d04df410dc3b1046bc843a19d73e5/io/src/main/scala/fs2/io/io.scala#L140-L141
  private[http4s] def invokeCallback[F[_]](logger: Logger)(f: => Unit)(
      implicit F: ConcurrentEffect[F]): Unit =
    F.runAsync(F.start(F.delay(f)).flatMap(_.join))(loggingAsyncCallback(logger)).unsafeRunSync()

  /** Hex encoding digits. Adapted from apache commons Hex.encodeHex **/
  private val Digits: Array[Char] =
    Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  /** Encode a byte Array into a hexadecimal string
    *
    * @param data the array
    * @return a hexadecimal encoded string
    */
  private[http4s] final def encodeHexString(data: Array[Byte]): String =
    new String(encodeHex(data))

  /** Encode a string to a Hexadecimal string representation
    * Adapted from apache commons Hex.encodeHex
    */
  private[http4s] final def encodeHex(data: Array[Byte]): Array[Char] = {
    val l = data.length
    val out = new Array[Char](l << 1)
    // two characters form the hex value.
    def iterateData(out: Array[Char], l: Int): Array[Char] = {
      def innerEncode(l: Int, i: Int, j: Int): Array[Char] =
        i match {
          case k if k < l =>
            out(j) = Digits((0xF0 & data(k)) >>> 4)
            out(j + 1) = Digits(0x0F & data(k))
            innerEncode(l, k + 1, j + 2)
          case _ => out
        }
      innerEncode(l, 0, 0)
    }
    iterateData(out, l)
  }

  private[http4s] final def decodeHexString(data: String): Option[Array[Byte]] =
    decodeHex(data.toCharArray)

  private object HexDecodeException extends Exception with NoStackTrace

  /** Dirty, optimized hex decoding based off of apache
    * common hex decoding, ported over to scala
    *
    * @param data
    * @return
    */
  private[http4s] final def decodeHex(data: Array[Char]): Option[Array[Byte]] = {
    def toDigit(ch: Char): Int = {
      val digit = Character.digit(ch, 16)
      if (digit == -1)
        throw HexDecodeException
      else
        digit
    }

    val len = data.length
    if ((len & 0x01) != 0) None
    val out = new Array[Byte](len >> 1)
    var f: Int = -1
    // two characters form the hex value.
    try {
      var i = 0
      var j = 0
      while (j < len) {
        f = toDigit(data(j)) << 4
        j += 1
        f = f | toDigit(data(j))
        j += 1
        out(i) = (f & 0xFF).toByte

        i += 1
      }
      Some(out)
    } catch {
      case HexDecodeException => None
    }
  }

  // Adapted from https://github.com/typelevel/cats-effect/issues/199#issuecomment-401273282
  private[http4s] def fromFuture[F[_], A](f: F[Future[A]])(implicit F: Async[F]): F[A] =
    f.flatMap { future =>
      future.value match {
        case Some(value) =>
          F.fromTry(value)
        case None =>
          F.async { cb =>
            future.onComplete {
              case Success(a) => cb(Right(a))
              case Failure(t) => cb(Left(t))
            }(direct)
          }
      }
    }

  // Adapted from https://github.com/typelevel/cats-effect/issues/160#issue-306054982
  @deprecated("Use `fromCompletionStage`", since = "0.21.2")
  private[http4s] def fromCompletableFuture[F[_], A](fcf: F[CompletableFuture[A]])(
      implicit F: Concurrent[F]): F[A] =
    fcf.flatMap { cf =>
      F.cancelable { cb =>
        cf.handle[Unit]((result, err) =>
          err match {
            case null => cb(Right(result))
            case _: CancellationException => ()
            case ex: CompletionException if ex.getCause ne null => cb(Left(ex.getCause))
            case ex => cb(Left(ex))
          })
        F.delay { cf.cancel(true); () }
      }
    }

  private[http4s] def fromCompletionStage[F[_], CF[x] <: CompletionStage[x], A](
      fcs: F[CF[A]])(implicit F: Async[F], CS: ContextShift[F]): F[A] =
    fcs.flatMap { cs =>
      F.async[A] { cb =>
          cs.handle[Unit] { (result, err) =>
            err match {
              case null => cb(Right(result))
              case _: CancellationException => ()
              case ex: CompletionException if ex.getCause ne null => cb(Left(ex.getCause))
              case ex => cb(Left(ex))
            }
          }
          ()
        }
        .guarantee(CS.shift)
    }

  private[http4s] def unsafeToCompletionStage[F[_], A](
      fa: F[A]
  )(implicit F: Effect[F]): CompletionStage[A] = {
    val cf = new CompletableFuture[A]()
    F.runAsync(fa) {
        case Right(a) => IO { cf.complete(a); () }
        case Left(e) => IO { cf.completeExceptionally(e); () }
      }
      .unsafeRunSync()
    cf
  }
}
