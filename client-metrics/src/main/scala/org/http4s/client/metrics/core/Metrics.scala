package org.http4s.client.metrics.core

import cats.effect.{Clock, Resource, Sync}
import cats.implicits._
import java.util.concurrent.TimeUnit
import org.http4s.{Request, Response}
import org.http4s.client.Client
import scala.concurrent.TimeoutException

object Metrics {
  def apply[F[_], R: MetricsOpsFactory](
      registry: R,
      prefix: String = "org.http4s.client",
      destination: Request[F] => Option[String] = { _: Request[F] =>
        None
      }
  )(client: Client[F])(implicit F: Sync[F], clock: Clock[F]): Client[F] = {
    val ops = implicitly[MetricsOpsFactory[R]].instance[F](registry, prefix)

    def withMetrics(req: Request[F]): Resource[F, Response[F]] =
      (for {
        start <- Resource.liftF(clock.monotonic(TimeUnit.NANOSECONDS))
        _ <- Resource.liftF(ops.increaseActiveRequests(destination(req)))
        resp <- client.run(req)
        end <- Resource.liftF(clock.monotonic(TimeUnit.NANOSECONDS))
        _ <- Resource.liftF(
          ops.registerRequestHeadersTime(resp.status, end - start, destination(req)))
        _ <- Resource.liftF(ops.decreaseActiveRequests(destination(req)))
        elapsed <- Resource.liftF(clock.monotonic(TimeUnit.NANOSECONDS).map(now => now - start))
        _ <- Resource.liftF(ops.registerRequestTotalTime(resp.status, elapsed, destination(req)))
      } yield resp).handleErrorWith { e: Throwable =>
        Resource.liftF[F, Response[F]](
          ops.decreaseActiveRequests(destination(req)) *> registerError(req, e) *>
            F.raiseError[Response[F]](e)
        )
      }

    def registerError(req: Request[F], e: Throwable): F[Unit] =
      if (e.isInstanceOf[TimeoutException]) {
        ops.increaseTimeouts(destination(req))
      } else {
        ops.increaseErrors(destination(req))
      }

    Client(withMetrics)
  }

}
