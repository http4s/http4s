/*
 * Copyright 2014 http4s.org
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
package blaze
package client

import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.syntax.all._
import fs2.Hotswap
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.ResponseHeaderTimeoutStage
import org.http4s.client.Client
import org.http4s.client.DefaultClient
import org.http4s.client.RequestKey
import org.log4s.getLogger

import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** Blaze client implementation */
object BlazeClient {

  /** Construct a new [[Client]] using blaze components
    *
    * @param manager source for acquiring and releasing connections. Not owned by the returned client.
    * @param config blaze client configuration.
    * @param onShutdown arbitrary tasks that will be executed when this client is shutdown
    */
  @deprecated("Use BlazeClientBuilder", "0.19.0-M2")
  def apply[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      config: BlazeClientConfig,
      onShutdown: F[Unit],
      ec: ExecutionContext,
  )(implicit F: ConcurrentEffect[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      requestTimeout = config.requestTimeout,
      scheduler = bits.ClientTickWheel,
      ec = ec,
      retries = 0,
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext,
      retries: Int,
  )(implicit F: ConcurrentEffect[F]): Client[F] =
    new BlazeClient[F, A](manager, responseHeaderTimeout, requestTimeout, scheduler, ec, retries)

  @deprecated("Preserved for binary compatibility", "0.22.9")
  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext,
  )(implicit F: ConcurrentEffect[F]): Client[F] =
    new BlazeClient[F, A](manager, responseHeaderTimeout, requestTimeout, scheduler, ec, 0)
}

private class BlazeClient[F[_], A <: BlazeConnection[F]](
    manager: ConnectionManager[F, A],
    responseHeaderTimeout: Duration,
    requestTimeout: Duration,
    scheduler: TickWheelExecutor,
    ec: ExecutionContext,
    retries: Int,
)(implicit F: ConcurrentEffect[F])
    extends DefaultClient[F] {

  private[this] val logger = getLogger

  override def run(req: Request[F]): Resource[F, Response[F]] =
    Hotswap.create[F, Either[Throwable, Response[F]]].flatMap { hotswap =>
      Resource.eval(retryLoop(req, retries, hotswap))
    }

  // A light implementation of the Retry middleware.  That needs a
  // Timer, which we don't have.
  private def retryLoop(
      req: Request[F],
      remaining: Int,
      hotswap: Hotswap[F, Either[Throwable, Response[F]]],
  ): F[Response[F]] =
    hotswap.clear *> // Release the prior connection before allocating the next, or we can deadlock the pool
      hotswap.swap(respond(req).attempt).flatMap {
        case Right(response) =>
          F.pure(response)
        case Left(_: SocketException) if remaining > 0 && req.isIdempotent =>
          val key = RequestKey.fromRequest(req)
          logger.debug(
            s"Encountered a SocketException on ${key}.  Retrying up to ${remaining} more times."
          )
          retryLoop(req, remaining - 1, hotswap)
        case Left(e) =>
          F.raiseError(e)
      }

  private def respond(req: Request[F]): Resource[F, Response[F]] = {
    val key = RequestKey.fromRequest(req)
    for {
      requestTimeoutF <- scheduleRequestTimeout(key)
      preparedConnection <- prepareConnection(key)
      (conn, responseHeaderTimeoutF) = preparedConnection
      timeout = responseHeaderTimeoutF.race(requestTimeoutF).map(_.merge)
      responseResource <- Resource.eval(runRequest(conn, req, timeout))
      response <- responseResource
    } yield response
  }

  private def prepareConnection(key: RequestKey): Resource[F, (A, F[TimeoutException])] = for {
    conn <- borrowConnection(key)
    responseHeaderTimeoutF <- addResponseHeaderTimeout(conn)
  } yield (conn, responseHeaderTimeoutF)

  private def borrowConnection(key: RequestKey): Resource[F, A] =
    Resource.makeCase(manager.borrow(key).map(_.connection)) {
      case (conn, ExitCase.Canceled) =>
        // Currently we can't just release in case of cancellation, because cancellation clears the Write state of Http1Connection, so it might result in isRecycle=true even if there's a half-written request.
        manager.invalidate(conn)
      case (conn, _) => manager.release(conn)
    }

  private def addResponseHeaderTimeout(conn: A): Resource[F, F[TimeoutException]] =
    responseHeaderTimeout match {
      case d: FiniteDuration =>
        Resource.apply(
          Deferred[F, Either[Throwable, TimeoutException]].flatMap(timeout =>
            F.delay {
              val stage = new ResponseHeaderTimeoutStage[ByteBuffer](d, scheduler, ec)
              conn.spliceBefore(stage)
              stage.init(e => timeout.complete(e).runAsync(_ => IO.unit).unsafeRunSync())
              (timeout.get.rethrow, F.delay(stage.removeStage()))
            }
          )
        )
      case _ => resourceNeverTimeoutException
    }

  private def scheduleRequestTimeout(key: RequestKey): Resource[F, F[TimeoutException]] =
    requestTimeout match {
      case d: FiniteDuration =>
        F.cancelable[TimeoutException] { cb =>
          val c = scheduler.schedule(
            () =>
              cb(Right(new TimeoutException(s"Request to $key timed out after ${d.toMillis} ms"))),
            ec,
            d,
          )
          F.delay(c.cancel())
        }.background
      case _ => resourceNeverTimeoutException
    }

  private def runRequest(
      conn: A,
      req: Request[F],
      timeout: F[TimeoutException],
  ): F[Resource[F, Response[F]]] =
    conn
      .runRequest(req, timeout)
      .race(timeout.flatMap(F.raiseError[Resource[F, Response[F]]](_)))
      .map(_.merge)

  private val resourceNeverTimeoutException = Resource.pure[F, F[TimeoutException]](F.never)

}
