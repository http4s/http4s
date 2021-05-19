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

import cats.effect.kernel.{Async, Resource}
import cats.effect.implicits._
import cats.syntax.all._
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{IdleTimeoutStage, ResponseHeaderTimeoutStage}
import org.http4s.client.{Client, RequestKey}
import org.log4s.getLogger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** Blaze client implementation */
object BlazeClient {
  import Resource.ExitCase

  private[this] val logger = getLogger

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext
  )(implicit F: Async[F]) =
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
            case (_, ExitCase.Succeeded) =>
              F.unit
            case (next, ExitCase.Errored(_) | ExitCase.Canceled) =>
              invalidate(next.connection)
          }

        def idleTimeoutStage(conn: A): Resource[F, Option[IdleTimeoutStage[ByteBuffer]]] =
          Resource.makeCase {
            idleTimeout match {
              case d: FiniteDuration =>
                val stage = new IdleTimeoutStage[ByteBuffer](d, scheduler, ec)
                F.delay(conn.spliceBefore(stage)).as(Some(stage))
              case _ =>
                F.pure(None)
            }
          } {
            case (_, ExitCase.Succeeded) => F.unit
            case (stageOpt, _) => F.delay(stageOpt.foreach(_.removeStage()))
          }

        def loop: F[Resource[F, Response[F]]] =
          borrow.use { next =>
            idleTimeoutStage(next.connection).use { stageOpt =>
              val idleTimeoutF = stageOpt match {
                case Some(stage) =>
                  F.async[TimeoutException] { cb =>
                    F.delay(stage.init(cb)).as(None)
                  }
                case None => F.never[TimeoutException]
              }
              val res = next.connection
                .runRequest(req, idleTimeoutF)
                .map { r =>
                  Resource.makeCase(F.pure(r)) {
                    case (_, ExitCase.Succeeded) =>
                      F.delay(stageOpt.foreach(_.removeStage()))
                        .guarantee(manager.release(next.connection))
                    case _ =>
                      F.delay(stageOpt.foreach(_.removeStage()))
                        .guarantee(manager.invalidate(next.connection))
                  }
                }

              responseHeaderTimeout match {
                case responseHeaderTimeout: FiniteDuration =>
                  F.deferred[Unit].flatMap { gate =>
                    val responseHeaderTimeoutF: F[TimeoutException] =
                      F.delay {
                        val stage =
                          new ResponseHeaderTimeoutStage[ByteBuffer](
                            responseHeaderTimeout,
                            scheduler,
                            ec)
                        next.connection.spliceBefore(stage)
                        stage
                      }.bracket { stage =>
                        F.async[TimeoutException] { cb =>
                          F.delay(stage.init(cb)) >> gate.complete(()).as(None)
                        }
                      }(stage => F.delay(stage.removeStage()))

                    F.race(gate.get *> res, responseHeaderTimeoutF)
                      .flatMap[Resource[F, Response[F]]] {
                        case Left(r) => F.pure(r)
                        case Right(t) => F.raiseError(t)
                      }
                  }
                case _ => res
              }
            }
          }

        val res = loop
        requestTimeout match {
          case d: FiniteDuration =>
            F.race(
              res,
              F.async[TimeoutException] { cb =>
                F.delay {
                  scheduler.schedule(
                    new Runnable {
                      def run() =
                        cb(Right(new TimeoutException(
                          s"Request to $key timed out after ${d.toMillis} ms")))
                    },
                    ec,
                    d
                  )
                }.map(c => Some(F.delay(c.cancel())))
              }
            ).flatMap[Resource[F, Response[F]]] {
              case Left(r) => F.pure(r)
              case Right(t) => F.raiseError(t)
            }
          case _ =>
            res
        }
      }
    }
}
