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
import cats.effect.kernel.Clock
import cats.effect.syntax.clock._
import cats.syntax.all._
import fs2._
import fs2.io.net.Socket
import org.http4s.headers.Connection
import org.typelevel.ci._

import java.time.Instant
import java.util.Arrays
import java.util.Locale
import scala.concurrent.duration._

private[ember] object Util extends UtilPlatform {

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
      chunkSize: Int,
  )(implicit F: ApplicativeThrow[F], C: Clock[F]): Stream[F, Byte] = {
    def whenWontTimeout: Stream[F, Byte] =
      socket.reads

    def whenMayTimeout(remains: FiniteDuration): Stream[F, Byte] =
      if (remains <= 0.millis)
        streamCurrentTimeMillis(C)
          .flatMap(now =>
            Stream.raiseError[F](
              EmberException.Timeout(Instant.ofEpochMilli(started), Instant.ofEpochMilli(now))
            )
          )
      else
        for {
          (processingTime, read) <- Stream.eval(socket.read(chunkSize).timed) //  Each Read Yields
          out <- read match {
            case None => Stream.empty
            case Some(chunk) => Stream.chunk(chunk) ++ go(remains - processingTime)
          }
        } yield out

    def go(remains: FiniteDuration): Stream[F, Byte] =
      Stream
        .eval(shallTimeout)
        .ifM(
          whenMayTimeout(remains),
          whenWontTimeout,
        )

    go(timeout)
  }

  def connectionFor(httpVersion: HttpVersion, headers: Headers): Connection =
    if (isKeepAlive(httpVersion, headers)) keepAlive
    else close

  def isKeepAlive(httpVersion: HttpVersion, headers: Headers): Boolean = {
    // TODO: the problem is that any string that contains `expected` is admissible
    def hasConnection(expected: String): Boolean =
      headers.headers.exists { case Header.Raw(name, value) =>
        name == connectionCi && value.toLowerCase(Locale.ROOT).contains(expected)
      }

    httpVersion match {
      case HttpVersion.`HTTP/1.0` => hasConnection(keepAliveCi.toString)
      case HttpVersion.`HTTP/1.1` => !hasConnection(closeCi.toString)
      case _ => sys.error("unsupported http version")
    }
  }

  def concatBytes(a1: Array[Byte], a2: Chunk[Byte]): Array[Byte] =
    if (a1.length == 0) {
      a2 match {
        case slice: Chunk.ArraySlice[Byte]
            if slice.values.isInstanceOf[Array[Byte]] &&
              slice.offset == 0 &&
              slice.values.length == slice.length =>
          slice.values
        case _ => a2.toArray
      }
    } else {
      val res = Arrays.copyOf(a1, a1.length + a2.size)
      a2.copyToArray(res, a1.length)
      res
    }

}
