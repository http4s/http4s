package org.http4s.client.metrics

import cats.data.Kleisli
import cats.effect.{Clock, Sync}
import cats.implicits._
import com.codahale.metrics.{Counter, MetricRegistry, Timer => MetricTimer}
import java.util.concurrent.TimeUnit
import org.http4s.{Request, Status}
import org.http4s.client.{Client, DisposableResponse}

object Metrics {
  def apply[F[_]](
      registry: MetricRegistry,
      prefix: String = "org.http4s.client",
      destination: Request[F] => Option[String] = { _: Request[F] =>
        None
      })(client: Client[F])(implicit F: Sync[F], clock: Clock[F]): Client[F] = {

    def withMetrics()(req: Request[F]): F[DisposableResponse[F]] = {
      val namespace = destination(req).map(d => s"${prefix}.${d}").getOrElse(s"${prefix}.default")
      for {
        start <- clock.monotonic(TimeUnit.NANOSECONDS)
        _ <- Sync[F].delay(registry.counter(s"${namespace}.active-requests").inc())
        resp <- client.open(req)
        now <- clock.monotonic(TimeUnit.NANOSECONDS)
        _ <- Sync[F].delay(
          registry
            .timer(s"${namespace}.requests.headers")
            .update(now - start, TimeUnit.NANOSECONDS))
        iResp <- Sync[F].delay(instrumentResponse(start, namespace, resp))
      } yield iResp
    }

    def instrumentResponse(
        start: Long,
        namespace: String,
        disposableResponse: DisposableResponse[F]): DisposableResponse[F] = {
      val newDisposable = for {
        _ <- Sync[F].delay(registry.counter(s"${namespace}.active-requests").dec())
        elapsed <- clock.monotonic(TimeUnit.NANOSECONDS).map(now => now - start)
        _ <- Sync[F].delay(updateMetrics(disposableResponse.response.status, elapsed, namespace))
        _ <- disposableResponse.dispose
      } yield ()

      disposableResponse.copy(dispose = newDisposable)
    }

    def updateMetrics(status: Status, elapsed: Long, namespace: String): Unit = {
      registry.timer(s"${namespace}.requests.total").update(elapsed, TimeUnit.NANOSECONDS)
      status.code match {
        case hundreds if hundreds < 200 => registry.counter(s"${namespace}.1xx-responses").inc()
        case twohundreds if twohundreds < 300 =>
          registry.counter(s"${namespace}.2xx-responses").inc()
        case threehundreds if threehundreds < 400 =>
          registry.counter(s"${namespace}.3xx-responses").inc()
        case fourhundreds if fourhundreds < 500 =>
          registry.counter(s"${namespace}.4xx-responses").inc()
        case _ => registry.counter(s"${namespace}.5xx-responses").inc()
      }
    }

    client.copy(open = Kleisli(withMetrics()))
  }

}

private case class MetricsCollection(
    activeRequests: Counter,
    requestsHeaders: MetricTimer,
    requestsTotal: MetricTimer,
    resp1xx: Counter,
    resp2xx: Counter,
    resp3xx: Counter,
    resp4xx: Counter,
    resp5xx: Counter
)
