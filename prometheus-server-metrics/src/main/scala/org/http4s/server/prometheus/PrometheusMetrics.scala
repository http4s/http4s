package org.http4s.server.prometheus

import io.prometheus.client._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s._
import org.http4s.server.HttpMiddleware

object PrometheusMetrics {

  private case class SingleMetricRun(
    activeRequests: Gauge,
    totalRequests: Histogram.Timer,
    requestTypeTimer: Histogram.Timer,
    headers: Histogram.Timer,
    abnormalTerminations: Counter,
    serviceErrors: Counter,
  )

  private case class GeneralServicMetrics(
    activeRequests: Gauge,
    totalRequests: Histogram,
    headers: Histogram,
    abnormalTerminations: Counter,
    serviceErrors: Counter,
  )

  private case class RequestHistograms(
    getReq: Histogram,
    postReq: Histogram,
    putReq: Histogram,
    headReq: Histogram,
    moveReq: Histogram,
    optionsReq: Histogram,
    traceReq: Histogram,
    connectReq: Histogram,
    deleteReq: Histogram,
    otherReq: Histogram
  )

  private case class ServiceMetrics(
    generalMetrics: GeneralServicMetrics,
    requestHist: RequestHistograms
  )

  private def metricsService[F[_]: Sync](
    singleMetricRun: SingleMetricRun, 
    service: HttpService[F]
  )(
    req: Request[F]
  ): OptionT[F, Response[F]] = OptionT{
    for {
      responseAtt <- service(req).value.attempt
      _ <- Sync[F].delay(singleMetricRun.headers.observeDuration)
      respOpt <- metricsServiceHandler[F](singleMetricRun, responseAtt)
    } yield respOpt
  }

  private def metricsServiceHandler[F[_]: Sync](
    s: SingleMetricRun,
    e: Either[Throwable, Option[Response[F]]]
  ): F[Option[Response[F]]] = {
    e.fold(
      e => onServiceError(s) *> Sync[F].raiseError[Option[Response[F]]](e),
      _.fold(
        Sync[F].delay(s.activeRequests.dec()).as(Option.empty[Response[F]])
      )(
        onResponse(s)(_).some.pure[F]
      )
    )
  }


  private def onResponse[F[_]: Sync](s: SingleMetricRun)(r: Response[F]): Response[F] = {
    val newBody = r.body
      .onFinalize(requestMetrics(s))
      .handleErrorWith(e =>
        Stream.eval(Sync[F].delay(
          s.abnormalTerminations.inc()
        )) *> Stream.raiseError[Byte](e)
      )
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](s: SingleMetricRun): F[Unit] = for {
    _ <- requestMetrics(s)
    _ <- Sync[F].delay(s.serviceErrors.inc())
  } yield ()



  private def requestMetrics[F[_]: Sync](s: SingleMetricRun): F[Unit] = Sync[F].delay{
    s.requestTypeTimer.observeDuration()
    s.totalRequests.observeDuration()
    s.activeRequests.dec()
  }

  private def requestHistogram[F[_]: Sync](rt: RequestHistograms, method: Method): F[Histogram.Timer] = Sync[F].delay(method match {
    case Method.GET => rt.getReq.startTimer
    case Method.POST => rt.postReq.startTimer
    case Method.PUT => rt.putReq.startTimer
    case Method.HEAD => rt.headReq.startTimer
    case Method.MOVE => rt.moveReq.startTimer
    case Method.OPTIONS => rt.optionsReq.startTimer
    case Method.TRACE => rt.traceReq.startTimer
    case Method.CONNECT => rt.connectReq.startTimer
    case Method.DELETE => rt.deleteReq.startTimer
    case _ => rt.otherReq.startTimer
  })

    private def startedRun[F[_]: Sync](serviceMetrics: ServiceMetrics, method: Method): F[SingleMetricRun] = for {
    requestTypeTimer <- requestHistogram(serviceMetrics.requestHist, method)
    result <- Sync[F].delay{
      serviceMetrics.generalMetrics.activeRequests.inc()

      SingleMetricRun(
        serviceMetrics.generalMetrics.activeRequests,
        serviceMetrics.generalMetrics.totalRequests.startTimer,
        requestTypeTimer,
        serviceMetrics.generalMetrics.headers.startTimer,
        serviceMetrics.generalMetrics.abnormalTerminations,
        serviceMetrics.generalMetrics.serviceErrors
      )

    }
  } yield result

  def apply[F[_]: Effect](prefix: String = "org_http4s_server", c: CollectorRegistry): F[HttpMiddleware[F]] = Sync[F].delay{
    val generalServiceMetrics = GeneralServicMetrics(
      activeRequests = Gauge.build().name(prefix + "_" + "active_requests").help("Total Active Requests.").register(c),
      totalRequests = Histogram.build().name(prefix + "_" + "requests").help("Total Requests.").register(c),
      headers = Histogram.build().name(prefix + "_" + "headers_times").help("Header Timings.").register(c),
      abnormalTerminations = Counter.build().name(prefix + "_" + "abnormal_terminations").help("Abnormal Terminations.").register(c),
      serviceErrors = Counter.build().name(prefix + "_" + "service_errors").help("Service Errors.").register(c)
    )
    val requestHistograms = RequestHistograms(
      getReq = Histogram.build().name(prefix + "_" + "get_requests").help("GET Requests.").register(c),
      postReq = Histogram.build().name(prefix + "_" + "post_requests").help("POST Requests.").register(c),
      putReq = Histogram.build().name(prefix + "_" + "put_requests").help("PUT Requests.").register(c),
      headReq = Histogram.build().name(prefix + "_" + "head_requests").help("HEAD Requests.").register(c),
      moveReq = Histogram.build().name(prefix + "_" + "move_requests").help("MOVE Requests.").register(c),
      optionsReq = Histogram.build().name(prefix + "_" + "options_requests").help("OPTIONS Requests.").register(c),
      traceReq = Histogram.build().name(prefix + "_" + "trace_requests").help("TRACE Requests.").register(c),
      connectReq = Histogram.build().name(prefix + "_" + "connect_requests").help("CONNECT Requests.").register(c),
      deleteReq = Histogram.build().name(prefix + "_" + "delete_requests").help("DELETE Requests.").register(c),
      otherReq = Histogram.build().name(prefix + "_" + "other_requests").help("Other Requests.").register(c)
    )
    val serviceMetrics = ServiceMetrics(generalServiceMetrics, requestHistograms)
    
    { service: HttpService[F] => Kleisli(req => 
        OptionT.liftF(startedRun[F](serviceMetrics, req.method)).flatMap( singleMetricRun => 
          metricsService[F](singleMetricRun, service)(req)
        ))
    }
  }

}