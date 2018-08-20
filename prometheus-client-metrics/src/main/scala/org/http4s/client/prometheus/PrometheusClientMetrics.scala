package org.http4s.client.prometheus

import io.prometheus.client._
import cats.implicits._
import cats.data.Kleisli
import cats.effect.{Clock, Sync}
import java.util.concurrent.TimeUnit
import org.http4s._
import org.http4s.client.{Client, DisposableResponse}

object PrometheusClientMetrics {

  private def reportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

  private sealed trait ResponsePhase
  private object ResponsePhase {
    case object ResponseReceived extends ResponsePhase
    case object BodyProcessed extends ResponsePhase
    def report(s: ResponsePhase): String = s match {
      case ResponseReceived => "response_received"
      case BodyProcessed => "body_processed"
    }
  }

  private case class ClientMetrics[F[_]](
      destination: Request[F] => String,
      responseDuration: Histogram,
      activeRequests: Gauge,
      responseCounter: Counter,
      clientErrorsCounter: Counter
  )

  private def now[F[_]](implicit clock: Clock[F]): F[Long] =
    clock.monotonic(TimeUnit.NANOSECONDS)

  private def metricsClient[F[_]: Sync: Clock](
      metrics: ClientMetrics[F],
      client: Client[F]
  )(
      request: Request[F]
  ): F[DisposableResponse[F]] =
    for {
      startTime <- now
      _ <- Sync[F].delay(
        metrics.activeRequests
          .labels(metrics.destination(request))
          .inc())
      responseAttempt <- client.open(request).attempt
      responseReceivedTime <- now
      result <- responseAttempt.fold(
        e =>
          onClientError(request, metrics) *>
            Sync[F].raiseError[DisposableResponse[F]](e),
        dr =>
          onResponse(request, dr.response, startTime, responseReceivedTime, metrics).map(r =>
            dr.copy(response = r))
      )
    } yield result

  private def onClientError[F[_]: Sync](request: Request[F], metrics: ClientMetrics[F]): F[Unit] =
    Sync[F].delay {
      //not updating responseDuration or responseCounter, since we did not receive a response
      metrics.activeRequests
        .labels(metrics.destination(request))
        .dec()
      metrics.clientErrorsCounter
        .labels(metrics.destination(request))
        .inc()
    }

  private def onResponse[F[_]: Sync: Clock](
      request: Request[F],
      response: Response[F],
      startTime: Long,
      responseReceivedTime: Long,
      metrics: ClientMetrics[F]): F[Response[F]] =
    Sync[F].delay {
      metrics.responseDuration
        .labels(
          metrics.destination(request),
          reportStatus(response.status),
          ResponsePhase.report(ResponsePhase.ResponseReceived))
        .observe(SimpleTimer.elapsedSecondsFromNanos(startTime, responseReceivedTime))
      metrics.responseCounter
        .labels(metrics.destination(request), reportStatus(response.status))
        .inc()
      metrics.activeRequests
        .labels(metrics.destination(request))
        .dec()
      response.copy(body = response.body.onFinalize {
        now.map { bodyProcessedTime =>
          metrics.responseDuration
            .labels(
              metrics.destination(request),
              reportStatus(response.status),
              ResponsePhase.report(ResponsePhase.BodyProcessed))
            .observe(SimpleTimer.elapsedSecondsFromNanos(startTime, bodyProcessedTime))
        }
      })
    }

  def apply[F[_]: Sync: Clock](
      c: CollectorRegistry,
      prefix: String = "org_http4s_client",
      destination: Request[F] => String = { _: Request[F] =>
        ""
      }
  ): Kleisli[F, Client[F], Client[F]] =
    Kleisli { client =>
      Sync[F].delay {
        val clientMetrics = ClientMetrics[F](
          destination = destination,
          responseDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames("destination", "code", "response_phase")
            .register(c),
          activeRequests = Gauge
            .build()
            .name(prefix + "_" + "active_request_count")
            .help("Total Active Requests.")
            .labelNames("destination")
            .register(c),
          responseCounter = Counter
            .build()
            .name(prefix + "_" + "response_total")
            .help("Total Responses.")
            .labelNames("destination", "code")
            .register(c),
          clientErrorsCounter = Counter
            .build()
            .name(prefix + "_" + "client_errors_total")
            .help("Total Client Errors.")
            .labelNames("destination")
            .register(c)
        )
        client.copy(open = Kleisli(metricsClient[F](clientMetrics, client)(_)))
      }
    }
}
