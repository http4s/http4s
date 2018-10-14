package org.http4s
package client
package blaze

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import java.util.concurrent.TimeoutException
import org.http4s.blaze.pipeline.Command
import org.log4s.getLogger
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
      onShutdown: F[Unit])(implicit F: ConcurrentEffect[F], timer: Timer[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      idleTimeout = config.idleTimeout,
      requestTimeout = config.requestTimeout
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      requestTimeout: Duration
  )(implicit F: ConcurrentEffect[F], timer: Timer[F]) = {

    val raceRequestTimeout: F[Resource[F, Response[F]]] => F[Resource[F, Response[F]]] =
      requestTimeout match {
        case finite: FiniteDuration =>
          val timeout =
            timer
              .sleep(finite)
              .as(
                Resource.liftF(F.raiseError[Response[F]](
                  new TimeoutException(s"Request timeout after ${requestTimeout}"))))
          _.timeoutTo(finite, timeout)
        case _ =>
          identity
      }

    Client[F] { req =>
      Resource.suspend {
        val key = RequestKey.fromRequest(req)

        // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
        // but it should be noisy.
        def invalidate(connection: A): F[Unit] =
          manager
            .invalidate(connection)
            .handleError(e => logger.error(e)("Error invalidating connection"))

        def loop(
            next: manager.NextConnection,
            ts: ClientTimeoutStage): F[Resource[F, Response[F]]] =
          F.delay {
            // Add the timeout stage to the pipeline
            next.connection.spliceBefore(ts)
            ts.stageStartup()
          } >> next.connection.runRequest(req).attempt.flatMap {
            case Right(r) =>
              val dispose = F
                .delay(ts.removeStage)
                .flatMap { _ =>
                  manager.release(next.connection)
                }
              F.pure(Resource(F.pure(r -> dispose)))

            case Left(Command.EOF) =>
              invalidate(next.connection).flatMap { _ =>
                if (next.fresh)
                  F.raiseError(
                    new java.net.ConnectException(s"Failed to connect to endpoint: $key"))
                else {
                  manager.borrow(key).flatMap { newConn =>
                    loop(newConn, ts)
                  }
                }
              }

            case Left(e) =>
              invalidate(next.connection) *> F.raiseError(e)
          }

        Deferred[F, TimeoutException].flatMap { timedOut =>
          def timedOutCallback(e: Either[Throwable, TimeoutException]) =
            (e match {
              case Right(t) => timedOut.complete(t).attempt.void
              case Left(t) => F.raiseError(t)
            }).toIO.unsafeRunSync()
          val ts = new ClientTimeoutStage(
            responseHeaderTimeout,
            idleTimeout,
            bits.ClientTickWheel,
            timedOutCallback
          )
          val resp = manager.borrow(key).flatMap(loop(_, ts))
          F.racePair(raceRequestTimeout(resp), timedOut.get).flatMap {
            case Left((resp, fiber)) => fiber.cancel.as(resp)
            case Right((fiber, t)) => fiber.cancel.as(Resource.liftF(F.raiseError(t)))
          }
        }
      }
    }
  }
}
