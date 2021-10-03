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
import cats.implicits._

import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.ResponseHeaderTimeoutStage
import org.http4s.client.{Client, DefaultClient, RequestKey}
import org.log4s.getLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** Blaze client implementation */
object BlazeClient {
  private[this] val logger = getLogger

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
      ec: ExecutionContext)(implicit F: ConcurrentEffect[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      requestTimeout = config.requestTimeout,
      scheduler = bits.ClientTickWheel,
      ec = ec
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext
  )(implicit F: ConcurrentEffect[F]): Client[F] =
    new BlazeClient[F, A](manager, responseHeaderTimeout, requestTimeout, scheduler, ec)
}

private class BlazeClient[F[_], A <: BlazeConnection[F]](manager: ConnectionManager[F, A],
                  responseHeaderTimeout: Duration,
                  requestTimeout: Duration,
                  scheduler: TickWheelExecutor,
                  ec: ExecutionContext
                 )(implicit F: ConcurrentEffect[F]) extends DefaultClient[F]{

  override def run(req: Request[F]): Resource[F, Response[F]] = for {
    _ <- Resource.pure[F, Unit](())
    key = RequestKey.fromRequest(req)
    requestTimeoutF <- scheduleRequestTimeout(key)
    (conn, responseHeaderTimeoutF) <- prepareConnection(key)
    timeout = responseHeaderTimeoutF.race(requestTimeoutF).map(_.merge)
    responseResource <- Resource.eval(
      runRequest(conn, req, timeout).adaptError { case EOF =>
        new SocketException(s"HTTP connection closed: ${key}")
      }
    )
    response <- responseResource
  } yield response

  private def prepareConnection(key: RequestKey): Resource[F, (A, F[TimeoutException])] = for {
    conn <- borrowConnection(key)
    responseHeaderTimeoutF <- addResponseHeaderTimeout(conn)
  } yield (conn, responseHeaderTimeoutF)

  private def borrowConnection(key: RequestKey): Resource[F, A] =
    Resource.makeCase(manager.borrow(key).map(_.connection)) {
      case (conn, ExitCase.Canceled) => manager.invalidate(conn) // TODO why can't we just release and let the pool figure it out?
      case (conn, _) => manager.release(conn)
    }

  private def addResponseHeaderTimeout(conn: A): Resource[F, F[TimeoutException]] =
    responseHeaderTimeout match {
      case d: FiniteDuration =>
        Resource.apply(
          Deferred[F, Either[Throwable, TimeoutException]].flatMap(timeout => F.delay {
            val stage = new ResponseHeaderTimeoutStage[ByteBuffer](d, scheduler, ec)
            conn.spliceBefore(stage)
            stage.init(e => timeout.complete(e).toIO.unsafeRunSync())
            (timeout.get.rethrow, F.delay(stage.removeStage()))
          })
        )
      case _ => Resource.pure[F, F[TimeoutException]](F.never)
    }

  private def scheduleRequestTimeout(key: RequestKey): Resource[F, F[TimeoutException]] =
    requestTimeout match {
      case d: FiniteDuration =>
        F.cancelable[TimeoutException] { cb =>
          println("scheduling request timeout")
          val c = scheduler.schedule (
            () => {
              println("request timeout happened")
              cb(Right(new TimeoutException(s"Request to $key timed out after ${d.toMillis} ms")))
            },
            ec,
            d
          )
          F.delay{println("cancel"); c.cancel()}
        }.background.map(_.guaranteeCase(caze => F.delay(println(caze.toString))))
      case _ => Resource.pure[F, F[TimeoutException]](F.never)
    }

  private def runRequest(conn: A, req: Request[F], timeout: F[TimeoutException]): F[Resource[F, Response[F]]] =
    conn.runRequest(req, timeout)
      .race(timeout.flatMap(F.raiseError[Resource[F, Response[F]]](_))).map(_.merge)
      .flatTap(_ => F.delay("runRequest"))

}
