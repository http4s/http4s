package org.http4s.client.metrics

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import java.util.concurrent.TimeUnit
import org.http4s.{Request, Status}
import org.http4s.client.{Client, DisposableResponse}

object Metrics {
  def apply[F[_]: Sync](registry: MetricRegistry, prefix: String = "org.http4s.client")(
      client: Client[F]): Client[F] = {

    def withMetrics(metrics: MetricsCollection)(req: Request[F]): F[DisposableResponse[F]] =
      for {
        start <- Sync[F].delay(System.nanoTime())
        _ <- Sync[F].delay(metrics.activeRequests.inc())
        resp <- client.open(req)
        _ <- Sync[F].delay(metrics.activeRequests.dec())
        iResp <- Sync[F].delay(instrumentResponse(start, metrics, resp))
      } yield iResp

    def instrumentResponse(
        start: Long,
        metrics: MetricsCollection,
        disposableResponse: DisposableResponse[F]): DisposableResponse[F] = {
      val newDisposable = for {
        elapsed <- Sync[F].delay(System.nanoTime() - start)
        _ <- Sync[F].delay(updateMetrics(disposableResponse.response.status, elapsed, metrics))
        _ <- disposableResponse.dispose
      } yield ()

      disposableResponse.copy(dispose = newDisposable)
    }

    def updateMetrics(status: Status, elapsed: Long, metrics: MetricsCollection): Unit = {
      metrics.requests.update(elapsed, TimeUnit.NANOSECONDS)
      status.code match {
        case hundreds if hundreds < 200 => metrics.resp1xx.inc()
        case twohundreds if twohundreds < 300 => metrics.resp2xx.inc()
        case threehundreds if threehundreds < 400 => metrics.resp3xx.inc()
        case fourhundreds if fourhundreds < 500 => metrics.resp4xx.inc()
        case _ => metrics.resp5xx.inc()
      }
    }

    val metricsCollection = MetricsCollection(
      activeRequests = registry.counter(s"${prefix}.active-requests"),
      requests = registry.timer(s"${prefix}.requests"),
      resp1xx = registry.counter(s"${prefix}.1xx-responses"),
      resp2xx = registry.counter(s"${prefix}.2xx-responses"),
      resp3xx = registry.counter(s"${prefix}.3xx-responses"),
      resp4xx = registry.counter(s"${prefix}.4xx-responses"),
      resp5xx = registry.counter(s"${prefix}.5xx-responses")
    )

    client.copy(open = Kleisli(withMetrics(metricsCollection)))
  }

}

private case class MetricsCollection(
    activeRequests: Counter,
    requests: Timer,
    resp1xx: Counter,
    resp2xx: Counter,
    resp3xx: Counter,
    resp4xx: Counter,
    resp5xx: Counter
)
