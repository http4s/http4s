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

package org.http4s.ember.core

import cats._
import cats.effect.kernel.{Clock, Temporal}
import cats.syntax.all._
import fs2._
import fs2.io.net.Socket
import scala.concurrent.duration._
import java.time.Instant

private[ember] object Util {

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

}
