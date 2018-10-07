package org.http4s
package server
package metrics

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._

object Metrics {

  def apply[F[_]](ops: MetricsOps[F])(implicit F: Effect[F]): HttpMiddleware[F] = { service =>
    Kleisli(metricsService[F](ops, service)(_))
  }

  private def metricsService[F[_]: Sync](ops: MetricsOps[F], routes: HttpRoutes[F])(
      req: Request[F]): OptionT[F, Response[F]] = OptionT {
    for {
      now <- Sync[F].delay(System.nanoTime())
      _ <- ops.increaseActiveRequests()
      e <- routes(req).value.attempt
      resp <- metricsServiceHandler(req.method, now, ops, e)
    } yield resp
  }

  private def metricsServiceHandler[F[_]: Sync](
      method: Method,
      start: Long,
      ops: MetricsOps[F],
      e: Either[Throwable, Option[Response[F]]]
  ): F[Option[Response[F]]] = {
    for {
      elapsed <- EitherT.liftF[F, Throwable, Long](Sync[F].delay(System.nanoTime() - start))
      respOpt <- EitherT(
        e.bitraverse[F, Throwable, Option[Response[F]]](
          manageServiceErrors(method, elapsed, ops).as(_),
          _.map(manageResponse(method, start, elapsed, ops)).pure[F]
        ))
    } yield respOpt
  }.fold(
      Sync[F].raiseError[Option[Response[F]]],
      _.fold(handleUnmatched(ops))(handleMatched)
  ).flatten

  private def manageResponse[F[_]: Sync](
      method: Method,
      start: Long,
      elapsedInit: Long,
      ops: MetricsOps[F]
  )(response: Response[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          elapsed <- Sync[F].delay(System.nanoTime() - start)
          _ <- ops.recordHeadersTime(elapsedInit)
          _ <- ops.decreaseActiveRequests()
          _ <- ops.increaseRequests()
          _ <- ops.recordTotalTime(method, elapsed)
          _ <- ops.recordTotalTime(response.status, elapsed)
        } yield ()
      }
      .handleErrorWith(e =>
          Stream.eval(ops.increaseAbnormalTerminations(elapsedInit)) *> Stream.raiseError[F](e)
      )
    response.copy(body = newBody)
  }

  private def manageServiceErrors[F[_]: Sync](
      method: Method,
      elapsed: Long,
      ops: MetricsOps[F]): F[Unit] =
    ops.recordTotalTime(method, elapsed) *> ops.increaseRequests() *>
    ops.decreaseActiveRequests() *> ops.increaseErrors(elapsed)

  private def handleUnmatched[F[_]: Sync](ops: MetricsOps[F]): F[Option[Response[F]]] =
    ops.decreaseActiveRequests().as(Option.empty[Response[F]])

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] =
    resp.some.pure[F]

}
