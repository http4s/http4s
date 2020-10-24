/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import java.util.concurrent.{
  CancellationException,
  CompletableFuture,
  CompletionException,
  CompletionStage
}

import cats.effect.implicits._
import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import cats.implicits._
import fs2.{Chunk, Pipe, Pull, RaiseThrowable, Stream}
import java.nio.{ByteBuffer, CharBuffer}
import org.log4s.Logger

import scala.concurrent.{ExecutionContext}
import scala.util.control.NoStackTrace
import java.nio.charset.MalformedInputException
import java.nio.charset.UnmappableCharacterException

package object internal {
  // Like fs2.async.unsafeRunAsync before 1.0.  Convenient for when we
  // have an ExecutionContext but not a Timer.
  private[http4s] def unsafeRunAsync[F[_]: Async, A](
      fa: F[A])(f: Either[Throwable, A] => F[Unit], dispatcher: Dispatcher[F])(implicit
      ec: ExecutionContext): Unit =
    dispatcher.unsafeRunSync(fa.evalOn(ec).attemptTap(f).void)

  private[http4s] def loggingAsyncCallback[F[_], A](logger: Logger)(attempt: Either[Throwable, A])(
      implicit F: Sync[F]): F[Unit] =
    attempt match {
      case Left(e) => F.delay(logger.error(e)("Error in asynchronous callback"))
      case Right(_) => F.unit
    }

  /** Hex encoding digits. Adapted from apache commons Hex.encodeHex */
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
            out(j) = Digits((0xf0 & data(k)) >>> 4)
            out(j + 1) = Digits(0x0f & data(k))
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
        out(i) = (f & 0xff).toByte

        i += 1
      }
      Some(out)
    } catch {
      case HexDecodeException => None
    }
  }

  // Adapted from https://github.com/typelevel/cats-effect/issues/160#issue-306054982
  @deprecated("Use `fromCompletionStage`", since = "0.21.3")
  private[http4s] def fromCompletableFuture[F[_], A](fcf: F[CompletableFuture[A]])(implicit
      F: Async[F]): F[A] =
    fcf.flatMap { cf =>
      F.async { cb =>
        F.delay(cf.handle[Unit]((result, err) =>
          err match {
            case null => cb(Right(result))
            case _: CancellationException => ()
            case ex: CompletionException if ex.getCause ne null => cb(Left(ex.getCause))
            case ex => cb(Left(ex))
          })) >>
          F.pure(Some(F.delay(cf.cancel(true)).void))
      }
    }

  private[http4s] def fromCompletionStage[F[_], CF[x] <: CompletionStage[x], A](
      fcs: F[CF[A]])(implicit
      // Concurrent is intentional, see https://github.com/http4s/http4s/pull/3255#discussion_r395719880
      F: Async[F]): F[A] =
    fcs.flatMap { cs =>
      F.async_ { cb =>
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
    }

  private[http4s] def unsafeToCompletionStage[F[_], A](
      fa: F[A],
      dispatcher: Dispatcher[F]
  )(implicit F: Sync[F]): CompletionStage[A] = {
    val cf = new CompletableFuture[A]()
    dispatcher.unsafeRunSync(fa.attemptTap {
      case Right(a) => F.delay { cf.complete(a); () }
      case Left(e) => F.delay { cf.completeExceptionally(e); () }
    })
    cf
  }

  private[http4s] def bug(message: String): AssertionError =
    new AssertionError(
      s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  // TODO Remove in 1.0. We can do better with MurmurHash3.
  private[http4s] def hashLower(s: String): Int = {
    var h = 0
    var i = 0
    val len = s.length
    while (i < len) {
      // Strings are equal igoring case if either their uppercase or lowercase
      // forms are equal. Equality of one does not imply the other, so we need
      // to go in both directions. A character is not guaranteed to make this
      // round trip, but it doesn't matter as long as all equal characters
      // hash the same.
      h = h * 31 + Character.toLowerCase(Character.toUpperCase(s.charAt(i)))
      i += 1
    }
    h
  }

  def decode[F[_]: RaiseThrowable](charset: Charset): Pipe[F, Byte, String] = { in =>
    val decoder = charset.nioCharset.newDecoder
    val byteBufferSize = 16
    val byteBuffer = ByteBuffer.allocate(byteBufferSize)
    val charBuffer =
      CharBuffer.allocate(math.ceil(byteBufferSize.toDouble * decoder.averageCharsPerByte).toInt)

    def skipByteOrderMark(chunk: Chunk[Byte]): Chunk[Byte] =
      if (chunk.size >= 3 && chunk.take(3) == utf8Bom)
        chunk.drop(3)
      else chunk

    def out = {
      val s = charBuffer.flip().toString()
      charBuffer.clear()
      if (s.isEmpty) Pull.done else Pull.output1(s)
    }

    Pull
      .loop[F, String, Stream[F, Byte]] { stream =>
        stream.pull.unconsN(byteBuffer.remaining(), allowFewer = true).flatMap {
          case None =>
            byteBuffer.flip()
            val result = decoder.decode(byteBuffer, charBuffer, true)
            byteBuffer.compact()
            result match {
              case _ if result.isUnderflow =>
                def flushLoop: Pull[F, String, Unit] =
                  decoder.flush(charBuffer) match {
                    case result if result.isUnderflow =>
                      out
                    case result if result.isOverflow =>
                      out >> flushLoop
                  }
                flushLoop.as(None)
              case _ if result.isOverflow =>
                out.as(Some(Stream.empty))
              case _ if result.isMalformed =>
                Pull.raiseError(new MalformedInputException(result.length()))
              case _ if result.isUnmappable =>
                Pull.raiseError(new UnmappableCharacterException(result.length()))
            }
          case Some((chunk, stream)) =>
            val chunkWithoutBom = skipByteOrderMark(chunk)
            byteBuffer.put(chunkWithoutBom.toArray)
            byteBuffer.flip()
            val result = decoder.decode(byteBuffer, charBuffer, false)
            byteBuffer.compact()
            result match {
              case _ if result.isUnderflow || result.isOverflow =>
                out.as(Some(stream))
              case _ if result.isMalformed =>
                Pull.raiseError(new MalformedInputException(result.length()))
              case _ if result.isUnmappable =>
                Pull.raiseError(new UnmappableCharacterException(result.length()))
            }
        }
      }(in)
      .void
      .stream
  }

  private val utf8Bom: Chunk[Byte] = Chunk(0xef.toByte, 0xbb.toByte, 0xbf.toByte)

  private[http4s] def skipUtf8ByteOrderMark(chunk: Chunk[Byte]): Chunk[Byte] =
    if (chunk.size >= 3 && chunk.take(3) == utf8Bom)
      chunk.drop(3)
    else chunk
}
