/*
 * Copyright 2019 http4s.org
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
package ember.core

import cats._
import cats.data.NonEmptyList
import cats.effect.kernel.{Clock, Temporal}
import cats.syntax.all._
import fs2._
import fs2.io.net.Socket
import java.util.Locale
import org.http4s.headers.Connection
import org.typelevel.ci._
import scala.concurrent.duration._
import java.time.Instant

private[ember] object Util {

  private[this] val closeCi = ci"close"
  private[this] val keepAliveCi = ci"keep-alive"
  private[this] val connectionCi = ci"connection"
  private[this] val close = Connection(NonEmptyList.of(closeCi))
  private[this] val keepAlive = Connection(NonEmptyList.one(keepAliveCi))

  private def streamCurrentTimeMillis[F[_]](clock: Clock[F]): Stream[F, Long] =
    Stream
      .eval(clock.realTime)
      .map(_.toMillis)

  /** The issue with a normal http body is that there is no termination character,
    * thus unless you have content-length and the client still has their input side open,
    * the server cannot know whether more data follows or not
    * This means this Stream MUST be infinite and additional parsing is required.
    * To know how much client input to consume
    *
    * Function if timeout reads via socket read and then incrementally lowers
    * the remaining time after each read.
    * By setting the timeout signal outside this after the
    * headers have been read it triggers this function
    * to then not timeout on the remaining body.
    */
  def readWithTimeout[F[_]](
      socket: Socket[F],
      started: Long,
      timeout: FiniteDuration,
      shallTimeout: F[Boolean],
      chunkSize: Int
  )(implicit F: ApplicativeThrow[F], C: Clock[F]): Stream[F, Byte] = {
    def whenWontTimeout: Stream[F, Byte] =
      socket.reads
    def whenMayTimeout(remains: FiniteDuration): Stream[F, Byte] =
      if (remains <= 0.millis)
        streamCurrentTimeMillis(C)
          .flatMap(now =>
            Stream.raiseError[F](
              EmberException.Timeout(Instant.ofEpochMilli(started), Instant.ofEpochMilli(now))
            ))
      else
        for {
          start <- streamCurrentTimeMillis(C)
          read <- Stream.eval(socket.read(chunkSize)) //  Each Read Yields
          end <- streamCurrentTimeMillis(C)
          out <- read.fold[Stream[F, Byte]](
            Stream.empty
          )(
            Stream.chunk(_).covary[F] ++ go(remains - (end - start).millis)
          )
        } yield out
    def go(remains: FiniteDuration): Stream[F, Byte] =
      Stream
        .eval(shallTimeout)
        .ifM(
          whenMayTimeout(remains),
          whenWontTimeout
        )
    go(timeout)
  }

  def durationToFinite(duration: Duration): Option[FiniteDuration] = duration match {
    case f: FiniteDuration => Some(f)
    case _ => None
  }

  def timeoutMaybe[F[_], A](fa: F[A], d: Duration)(implicit F: Temporal[F]): F[A] =
    d match {
      case fd: FiniteDuration => F.timeout(fa, fd)
      case _ => fa
    }

  def timeoutToMaybe[F[_], A](fa: F[A], d: Duration, ft: F[A])(implicit F: Temporal[F]): F[A] =
    d match {
      case fd: FiniteDuration => F.timeoutTo(fa, fd, ft)
      case _ => fa
    }

  def connectionFor(httpVersion: HttpVersion, headers: Headers): Connection =
    if (isKeepAlive(httpVersion, headers)) keepAlive
    else close

  def isKeepAlive(httpVersion: HttpVersion, headers: Headers): Boolean = {
    // We know this is raw because we have not parsed any headers in the underlying alg.
    // If Headers are being parsed into processed for in ParseHeaders this is incorrect.
    // TODO: the problem is that any string that contains `expected` is admissible
    def hasConnection(expected: String): Boolean =
      headers.headers.exists {
        case Header.Raw(name, value) =>
          name == connectionCi && value.toLowerCase(Locale.ROOT).contains(expected)
        case _ => false
      }

    httpVersion match {
      case HttpVersion.`HTTP/1.0` => hasConnection(keepAliveCi.toString)
      case HttpVersion.`HTTP/1.1` => !hasConnection(closeCi.toString)
      case _ => sys.error("unsupported http version")
    }
  }

}
