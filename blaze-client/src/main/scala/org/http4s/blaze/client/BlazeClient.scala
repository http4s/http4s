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
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.ResponseHeaderTimeoutStage
import org.http4s.client.Client
import org.http4s.client.RequestKey
import org.log4s.getLogger

import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
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
      ec: ExecutionContext,
  )(implicit F: ConcurrentEffect[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      requestTimeout = config.requestTimeout,
      scheduler = bits.ClientTickWheel,
      ec = ec,
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext,
  )(implicit F: ConcurrentEffect[F]) =
    Client[F] { req =>
      Resource.suspend {
        val key = RequestKey.fromRequest(req)

        // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
        // but it should be noisy.
        def invalidate(connection: A): F[Unit] =
          manager
            .invalidate(connection)
            .handleError(e => logger.error(e)(s"Error invalidating connection for $key"))

        def borrow: Resource[F, manager.NextConnection] =
          Resource.makeCase(manager.borrow(key)) {
            case (_, ExitCase.Completed) =>
              F.unit
            case (next, ExitCase.Error(_) | ExitCase.Canceled) =>
              invalidate(next.connection)
          }

        def loop: F[Resource[F, Response[F]]] =
          borrow.use { next =>
            val res: F[Resource[F, Response[F]]] = next.connection
              .runRequest(req)
              .adaptError { case EOF =>
                new SocketException(s"HTTP connection closed: ${key}")
              }
              .map { (response: Resource[F, Response[F]]) =>
                response.flatMap(r =>
                  Resource.make(F.pure(r))(_ => manager.release(next.connection))
                )
              }

            responseHeaderTimeout match {
              case responseHeaderTimeout: FiniteDuration =>
                Deferred[F, Unit].flatMap { gate =>
                  val responseHeaderTimeoutF: F[TimeoutException] =
                    F.delay {
                      val stage =
                        new ResponseHeaderTimeoutStage[ByteBuffer](
                          responseHeaderTimeout,
                          scheduler,
                          ec,
                        )
                      next.connection.spliceBefore(stage)
                      stage
                    }.bracket(stage =>
                      F.asyncF[TimeoutException] { cb =>
                        F.delay(stage.init(cb)) >> gate.complete(())
                      }
                    )(stage => F.delay(stage.removeStage()))

                  F.racePair(gate.get *> res, responseHeaderTimeoutF)
                    .flatMap[Resource[F, Response[F]]] {
                      case Left((r, fiber)) => fiber.cancel.as(r)
                      case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
                    }
                }
              case _ => res
            }
          }

        val res = loop
        requestTimeout match {
          case d: FiniteDuration =>
            F.racePair(
              res,
              F.cancelable[TimeoutException] { cb =>
                val c = scheduler.schedule(
                  new Runnable {
                    def run() =
                      cb(
                        Right(
                          new TimeoutException(s"Request to $key timed out after ${d.toMillis} ms")
                        )
                      )
                  },
                  ec,
                  d,
                )
                F.delay(c.cancel())
              },
            ).flatMap[Resource[F, Response[F]]] {
              case Left((r, fiber)) => fiber.cancel.as(r)
              case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
            }
          case _ =>
            res
        }
      }
    }
}
