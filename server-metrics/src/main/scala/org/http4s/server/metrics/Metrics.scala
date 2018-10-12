package org.http4s
package server
package metrics

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._

object Metrics {

  def apply[F[_]](ops: MetricsOps[F], classifierF: Request[F] => Option[String] = { _: Request[F] =>
    None
  })(implicit F: Effect[F]): HttpMiddleware[F] = { service =>
    Kleisli(metricsService[F](ops, service, classifierF)(_))
  }

  private def metricsService[F[_]: Sync](ops: MetricsOps[F], routes: HttpRoutes[F], classifierF: Request[F] => Option[String])(
      req: Request[F]): OptionT[F, Response[F]] = OptionT {
    for {
      now <- Sync[F].delay(System.nanoTime())
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
  ): F[Option[Response[F]]] = {
    for {
      elapsed <- EitherT.liftF[F, Throwable, Long](Sync[F].delay(System.nanoTime() - start))
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
  )(response: Response[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          elapsed <- Sync[F].delay(System.nanoTime() - start)
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
      elapsed: Long,
      ops: MetricsOps[F],
      classifier: Option[String]): F[Unit] =
    ops.recordTotalTime(method, elapsed, classifier) *> ops.increaseRequests(elapsed, classifier) *>
    ops.decreaseActiveRequests(classifier) *> ops.increaseErrors(elapsed, classifier)

  private def handleUnmatched[F[_]: Sync](ops: MetricsOps[F], classifier: Option[String]): F[Option[Response[F]]] =
    ops.decreaseActiveRequests(classifier).as(Option.empty[Response[F]])

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] =
    resp.some.pure[F]

}
