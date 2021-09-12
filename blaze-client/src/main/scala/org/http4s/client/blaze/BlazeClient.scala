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
package client
package blaze


import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.implicits._
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{IdleTimeoutStage, ResponseHeaderTimeoutStage}
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
      idleTimeout = config.idleTimeout,
      requestTimeout = config.requestTimeout,
      scheduler = bits.ClientTickWheel,
      ec = ec
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext
  )(implicit F: ConcurrentEffect[F]): Client[F] =
    new BlazeClient[F, A](manager, responseHeaderTimeout, idleTimeout, requestTimeout, scheduler, ec)

//    Client[F] { req =>
//      Resource.suspend {
//        val key = RequestKey.fromRequest(req)
//
//        // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
//        // but it should be noisy.
//        def invalidate(connection: A): F[Unit] =
//          manager
//            .invalidate(connection)
//            .handleError(e => logger.error(e)(s"Error invalidating connection for $key"))
//
//        def borrow: Resource[F, manager.NextConnection] =
//          Resource.makeCase(manager.borrow(key)) {
//            case (_, ExitCase.Completed) =>
//              F.unit
//            case (next, ExitCase.Error(_) | ExitCase.Canceled) =>
//              invalidate(next.connection)
//          }
//
//        def idleTimeoutStage(conn: A) =
//          Resource.makeCase {
//            idleTimeout match {
//              case d: FiniteDuration =>
//                val stage = new IdleTimeoutStage[ByteBuffer](d, scheduler, ec)
//                F.delay(conn.spliceBefore(stage)).as(Some(stage))
//              case _ =>
//                F.pure(None)
//            }
//          } {
//            case (_, ExitCase.Completed) => F.unit
//            case (stageOpt, _) => F.delay(stageOpt.foreach(_.removeStage()))
//          }
//
//        def loop: F[Resource[F, Response[F]]] =
//          borrow.use { next =>
//            idleTimeoutStage(next.connection).use { stageOpt =>
//              val idleTimeoutF = stageOpt match {
//                case Some(stage) => F.async[TimeoutException](stage.init)
//                case None => F.never[TimeoutException]
//              }
//              val res: F[Resource[F, Response[F]]] = next.connection
//                .runRequest(req, idleTimeoutF)
//                .map { response: Resource[F, Response[F]] =>
//                  response.flatMap(r =>
//                    Resource.make(F.pure(r))(_ =>
//                      F.delay(stageOpt.foreach(_.removeStage()))
//                        .guarantee(manager.release(next.connection))))
//                }
//
//              responseHeaderTimeout match {
//                case responseHeaderTimeout: FiniteDuration =>
//                  Deferred[F, Unit].flatMap { gate =>
//                    val responseHeaderTimeoutF: F[TimeoutException] =
//                      F.delay {
//                        val stage =
//                          new ResponseHeaderTimeoutStage[ByteBuffer](
//                            responseHeaderTimeout,
//                            scheduler,
//                            ec)
//                        next.connection.spliceBefore(stage)
//                        stage
//                      }.bracket(stage =>
//                        F.asyncF[TimeoutException] { cb =>
//                          F.delay(stage.init(cb)) >> gate.complete(())
//                        })(stage => F.delay(stage.removeStage()))
//
//                    F.racePair(gate.get *> res, responseHeaderTimeoutF)
//                      .flatMap[Resource[F, Response[F]]] {
//                        case Left((r, fiber)) => fiber.cancel.as(r)
//                        case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
//                      }
//                  }
//                case _ => res
//              }
//            }
//          }
//
//        val res = loop
//        requestTimeout match {
//          case d: FiniteDuration =>
//            F.racePair(
//              res,
//              F.cancelable[TimeoutException] { cb =>
//                val c = scheduler.schedule(
//                  new Runnable {
//                    def run() =
//                      cb(Right(
//                        new TimeoutException(s"Request to $key timed out after ${d.toMillis} ms")))
//                  },
//                  ec,
//                  d)
//                F.delay(c.cancel())
//              }
//            ).flatMap[Resource[F, Response[F]]] {
//              case Left((r, fiber)) => fiber.cancel.as(r)
//              case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
//            }
//          case _ =>
//            res
//        }
//      }
//    }
}

private class BlazeClient[F[_], A <: BlazeConnection[F]](manager: ConnectionManager[F, A],
                  responseHeaderTimeout: Duration,
                  idleTimeout: Duration,
                  requestTimeout: Duration,
                  scheduler: TickWheelExecutor,
                  ec: ExecutionContext
                 )(implicit F: ConcurrentEffect[F]) extends DefaultClient[F]{

  override def run(req: Request[F]): Resource[F, Response[F]] = for {
    _ <- Resource.pure[F, Unit](())
    key = RequestKey.fromRequest(req)
    requestTimeoutF <- scheduleRequestTimeout(key)
    (conn, idleTimeoutF, responseHeaderTimeoutF) <- prepareConnection(key)
    timeout = idleTimeoutF
      .race(responseHeaderTimeoutF).map(_.merge)
      .race(requestTimeoutF).map(_.merge)
    responseResource <- Resource.eval(runRequest(conn, req, timeout))
    response <- responseResource
  } yield response

  private def prepareConnection(key: RequestKey): Resource[F, (A, F[TimeoutException], F[TimeoutException])] = for {
    conn <- borrowConnection(key)
    idleTimeoutF <- addIdleTimeout(conn)
    responseHeaderTimeoutF <- addResponseHeaderTimeout(conn)
  } yield (conn, idleTimeoutF, responseHeaderTimeoutF)

  private def borrowConnection(key: RequestKey): Resource[F, A] =
    Resource.makeCase(manager.borrow(key).map(_.connection)) {
      case (conn, ExitCase.Canceled) => manager.invalidate(conn) // TODO why can't we just release and let the pool figure it out?
      case (conn, _) => manager.release(conn)
    }

  private def addIdleTimeout(conn: A): Resource[F, F[TimeoutException]] =
    idleTimeout match {
      case d: FiniteDuration =>
        Resource.apply(
          Deferred[F, Either[Throwable, TimeoutException]].flatMap( timeout => F.delay {
            val stage = new IdleTimeoutStage[ByteBuffer](d, scheduler, ec)
            conn.spliceBefore(stage)
            stage.init(e => timeout.complete(e).toIO.unsafeRunSync())
            (timeout.get.rethrow, F.delay(stage.removeStage()))
          })
        )
      case _ => Resource.pure[F, F[TimeoutException]](F.never)
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
