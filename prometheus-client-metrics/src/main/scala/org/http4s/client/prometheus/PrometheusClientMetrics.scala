package org.http4s.client.prometheus

import io.prometheus.client._
import cats.implicits._
import cats.data.Kleisli
import cats.effect.Sync
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

  /** The value of this key in the request's attributes is used as the value for the destination metric label. */
  val Destination = AttributeKey[String]
  val EmptyDestination = ""
  private def reportDestination(attributes: AttributeMap, destination: String): String =
    attributes.get(Destination).getOrElse(destination)

  private case class ClientMetrics(
      destination: String,
      responseDuration: Histogram,
      activeRequests: Gauge,
      responseCounter: Counter,
      clientErrorsCounter: Counter
  )

  private def metricsClient[F[_]: Sync](
      metrics: ClientMetrics,
      client: Client[F]
  )(
      request: Request[F]
  ): F[DisposableResponse[F]] =
    for {
      start <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(
        metrics.activeRequests
          .labels(reportDestination(request.attributes, metrics.destination))
          .inc())
      responseAttempt <- client.open(request).attempt
      end <- Sync[F].delay(System.nanoTime())
      result <- responseAttempt.fold(
        e =>
          onClientError(request, metrics) *>
            Sync[F].raiseError[DisposableResponse[F]](e),
        r => onResponse(request, r.response, start, end, metrics) *> r.pure[F]
      )
    } yield result

  private def onClientError[F[_]: Sync](request: Request[F], metrics: ClientMetrics): F[Unit] =
    Sync[F].delay {
      //not updating responseDuration or responseCounter, since we did not receive a response
      metrics.activeRequests
        .labels(reportDestination(request.attributes, metrics.destination))
        .dec()
      metrics.clientErrorsCounter
        .labels(reportDestination(request.attributes, metrics.destination))
        .inc()
    }

  private def onResponse[F[_]: Sync](
      request: Request[F],
      response: Response[F],
      start: Long,
      end: Long,
      metrics: ClientMetrics): F[Unit] =
    Sync[F].delay {
      metrics.responseDuration
        .labels(
          reportDestination(request.attributes, metrics.destination),
          reportStatus(response.status))
        .observe(SimpleTimer.elapsedSecondsFromNanos(start, end))
      metrics.responseCounter
        .labels(
          reportDestination(request.attributes, metrics.destination),
          reportStatus(response.status))
        .inc()
      metrics.activeRequests
        .labels(reportDestination(request.attributes, metrics.destination))
        .dec()
    }

  def apply[F[_]: Sync](
      c: CollectorRegistry,
      prefix: String = "org_http4s_client",
      destination: String = EmptyDestination
  ): Kleisli[F, Client[F], Client[F]] =
    Kleisli { client =>
      Sync[F].delay {
        val clientMetrics = ClientMetrics(
          destination = destination,
          responseDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames("destination", "code")
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

  /** Client middleware that sets the destination attribute of every request to the specified value. */
  def setRequestDestination[F[_]: Sync](client: Client[F], destination: String): Client[F] =
    client.copy(open = Kleisli(r => client.open(r.withAttribute(Destination, destination))))
}
