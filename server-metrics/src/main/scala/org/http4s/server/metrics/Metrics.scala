package org.http4s
package server
package metrics

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import java.util.concurrent.TimeUnit

object Metrics {

  def apply[F[_]](ops: MetricsOps[F], classifierF: Request[F] => Option[String] = { _: Request[F] =>
    None
  })(implicit F: Effect[F], clock: Clock[F]): HttpMiddleware[F] = { service =>
    Kleisli(metricsService[F](ops, service, classifierF)(_))
  }

  private def metricsService[F[_]: Sync](ops: MetricsOps[F], routes: HttpRoutes[F], classifierF: Request[F] => Option[String])(
      req: Request[F])(implicit clock: Clock[F]): OptionT[F, Response[F]] = OptionT {
    for {
      now <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- ops.increaseActiveRequests(classifierF(req))
      e <- routes(req).value.attempt
      resp <- metricsServiceHandler(req.method, now, ops, e, classifierF(req))
    } yield resp
  }

  private def metricsServiceHandler[F[_]: Sync](
      method: Method,
      start: Long,
      ops: MetricsOps[F],
      e: Either[Throwable, Option[Response[F]]],
      classifier: Option[String]
  )(implicit clock: Clock[F]): F[Option[Response[F]]] = {
    for {
      now     <- EitherT.liftF[F, Throwable, Long](clock.monotonic(TimeUnit.NANOSECONDS))
      elapsed <- EitherT.liftF[F, Throwable, Long](Sync[F].delay(now - start))
      respOpt <- EitherT(
        e.bitraverse[F, Throwable, Option[Response[F]]](
          manageServiceErrors(method, elapsed, ops, classifier).as(_),
          _.map(manageResponse(method, start, elapsed, ops, classifier)).pure[F]
        ))
    } yield respOpt
  }.fold(
      Sync[F].raiseError[Option[Response[F]]],
      _.fold(handleUnmatched(ops, classifier))(handleMatched)
  ).flatten

  private def manageResponse[F[_]: Sync](
      method: Method,
      start: Long,
      elapsedInit: Long,
      ops: MetricsOps[F],
      classifier: Option[String]
  )(response: Response[F])(implicit clock: Clock[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          now     <- clock.monotonic(TimeUnit.NANOSECONDS)
          elapsed <- Sync[F].delay(now - start)
          _ <- ops.recordHeadersTime(elapsedInit, classifier)
          _ <- ops.decreaseActiveRequests(classifier)
          _ <- ops.increaseRequests(elapsedInit, classifier)
          _ <- ops.recordTotalTime(method, elapsed, classifier)
          _ <- ops.recordTotalTime(response.status, elapsed, classifier)
        } yield ()
      }
      .handleErrorWith(e =>
          Stream.eval(ops.increaseAbnormalTerminations(elapsedInit, classifier)) *> Stream.raiseError[F](e)
      )
    response.copy(body = newBody)
  }

  private def manageServiceErrors[F[_]: Sync](
      method: Method,
      elapsedInit: Long,
      ops: MetricsOps[F],
      classifier: Option[String]): F[Unit] =
    ops.recordHeadersTime(elapsedInit, classifier) *> ops.recordTotalTime(method, elapsedInit, classifier) *>
    ops.increaseRequests(elapsedInit, classifier) *>  ops.decreaseActiveRequests(classifier) *>
    ops.increaseErrors(elapsedInit, classifier)

  private def handleUnmatched[F[_]: Sync](ops: MetricsOps[F], classifier: Option[String]): F[Option[Response[F]]] =
    ops.decreaseActiveRequests(classifier).as(Option.empty[Response[F]])

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] =
    resp.some.pure[F]

}
