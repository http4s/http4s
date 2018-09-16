package org.http4s.client.metrics.core

import cats.data.Kleisli
import cats.effect.{Clock, Sync}
import cats.implicits._
import java.util.concurrent.TimeUnit
import org.http4s.Request
import org.http4s.client.{Client, DisposableResponse}
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

    def withMetrics()(req: Request[F]): F[DisposableResponse[F]] =
      (for {
        start <- clock.monotonic(TimeUnit.NANOSECONDS)
        _ <- ops.increaseActiveRequests(destination(req))
        resp <- client.open(req)
        end <- clock.monotonic(TimeUnit.NANOSECONDS)
        _ <- ops.registerRequestHeadersTime(resp.response.status, end - start, destination(req))
        iResp <- instrumentResponse(start, destination(req), resp)
      } yield iResp).handleErrorWith { e =>
        ops.decreaseActiveRequests(destination(req)) *> handleError(req, e) *>
          F.raiseError[DisposableResponse[F]](e)
      }

    def handleError(req: Request[F], e: Throwable): F[Unit] =
      if (e.isInstanceOf[TimeoutException]) {
        ops.increaseTimeouts(destination(req))
      } else {
        ops.increaseErrors(destination(req))
      }

    def instrumentResponse(
        start: Long,
        destination: Option[String],
        disposableResponse: DisposableResponse[F]
    ): F[DisposableResponse[F]] = {
      val newDisposable = for {
        _ <- ops.decreaseActiveRequests(destination)
        elapsed <- clock.monotonic(TimeUnit.NANOSECONDS).map(now => now - start)
        _ <- ops.registerRequestTotalTime(disposableResponse.response.status, elapsed, destination)
        _ <- disposableResponse.dispose
      } yield ()

      F.delay(disposableResponse.copy(dispose = newDisposable))
    }

    client.copy(open = Kleisli(withMetrics()))
  }

}
