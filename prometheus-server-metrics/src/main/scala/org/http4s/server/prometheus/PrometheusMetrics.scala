package org.http4s.server.prometheus

import io.prometheus.client._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s._
import org.http4s.server.HttpMiddleware

object PrometheusMetrics {
  private case class GeneralServicMetrics(
    activeRequests: Gauge,
    totalRequests: Histogram,
    headers: Histogram,
    abnormalTerminations: Histogram,
    serviceErrors: Histogram,
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

  private case class ResponseHistograms(
    resp1xx: Histogram,
    resp2xx: Histogram,
    resp3xx: Histogram,
    resp4xx: Histogram,
    resp5xx: Histogram
  )

  private case class ServiceMetrics(
    generalMetrics: GeneralServicMetrics,
    requestHist: RequestHistograms,
    responseHist: ResponseHistograms
  )

  private def metricsService[F[_]: Sync](
    serviceMetrics: ServiceMetrics, 
    service: HttpService[F]
  )(
    req: Request[F]
  ): OptionT[F, Response[F]] = OptionT{
    for {
      initialTime <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(serviceMetrics.generalMetrics.activeRequests.inc())
      responseAtt <- service(req).value.attempt
      headersElapsed <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(serviceMetrics.generalMetrics.headers.observe((headersElapsed - initialTime).toDouble))
      respOpt <- metricsServiceHandler[F](req.method, initialTime, serviceMetrics, responseAtt)
    } yield respOpt
  }

  private def metricsServiceHandler[F[_]: Sync](
    m: Method, 
    start: Long,
    serviceMetrics: ServiceMetrics,
    e: Either[Throwable, Option[Response[F]]]
  ): F[Option[Response[F]]] = {
    e.fold(
      e => onServiceError(m, start, serviceMetrics) *> Sync[F].raiseError[Option[Response[F]]](e),
      _.fold(
        Sync[F].delay(serviceMetrics.generalMetrics.activeRequests.dec()).as(Option.empty[Response[F]])
      )(
        onResponse(m, start, serviceMetrics)(_).some.pure[F]
      )
    )
  }


  private def onResponse[F[_]: Sync](m: Method, start: Long, serviceMetrics: ServiceMetrics)(r: Response[F]): Response[F] = {
    val newBody = r.body
      .onFinalize{
        for {
          finalBodyTime <- Sync[F].delay(System.nanoTime)
          _ <- requestMetrics(
            serviceMetrics.requestHist, 
            serviceMetrics.generalMetrics.totalRequests, 
            serviceMetrics.generalMetrics.activeRequests
          )(m, start, finalBodyTime)
          _ <- responseMetrics(
            serviceMetrics.responseHist,
            r.status,
            start,
            finalBodyTime
          )
        } yield ()
      }.handleErrorWith(e => 
        for {
          terminationTime <- Stream.eval(Sync[F].delay(System.nanoTime))
          _ <- Stream.eval(Sync[F].delay(
            serviceMetrics.generalMetrics.abnormalTerminations.observe((terminationTime - start).toDouble)
          ))
          result <- Stream.raiseError[Byte](e).covary[F]
        } yield result
      )
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](m: Method, begin: Long, sm: ServiceMetrics): F[Unit] = for {
    end <- Sync[F].delay(System.nanoTime)
    _ <- requestMetrics(
      sm.requestHist,
      sm.generalMetrics.totalRequests,
      sm.generalMetrics.activeRequests
    )(m, begin, end)
    _ <- Sync[F].delay(sm.generalMetrics.serviceErrors.observe((end - begin).toDouble))
  } yield ()



  private def requestMetrics[F[_]: Sync](rh: RequestHistograms, totalReqs: Histogram, activeReqs: Gauge)(m: Method, begin: Long, end: Long): F[Unit] = Sync[F].delay{
    val histogram = requestHistogram(rh, m)
    val elapsed = (end - begin).toDouble
    histogram.observe(elapsed)
    totalReqs.observe(elapsed)
    activeReqs.dec()
  }

  private def responseMetrics[F[_]: Sync](rh: ResponseHistograms, status: Status, begin: Long, end: Long): F[Unit] = Sync[F].delay{
    responseHistogram(rh, status).observe((end - begin).toDouble)
  }

  private def requestHistogram[F[_]: Sync](rt: RequestHistograms, method: Method): Histogram = method match {
    case Method.GET => rt.getReq
    case Method.POST => rt.postReq
    case Method.PUT => rt.putReq
    case Method.HEAD => rt.headReq
    case Method.MOVE => rt.moveReq
    case Method.OPTIONS => rt.optionsReq
    case Method.TRACE => rt.traceReq
    case Method.CONNECT => rt.connectReq
    case Method.DELETE => rt.deleteReq
    case _ => rt.otherReq
  }

  private def responseHistogram(responseTimers: ResponseHistograms, status: Status): Histogram =
    status.code match {
      case hundreds if hundreds < 200 => responseTimers.resp1xx
      case twohundreds if twohundreds < 300 => responseTimers.resp2xx
      case threehundreds if threehundreds < 400 => responseTimers.resp3xx
      case fourhundreds if fourhundreds < 500 => responseTimers.resp4xx
      case _ => responseTimers.resp5xx
}

  def apply[F[_]: Effect](prefix: String = "org_http4s_server", c: CollectorRegistry): F[HttpMiddleware[F]] = Sync[F].delay{
    val generalServiceMetrics = GeneralServicMetrics(
      activeRequests = Gauge.build().name(prefix + "_" + "active_requests").help("Total Active Requests.").register(c),
      totalRequests = Histogram.build().name(prefix + "_" + "requests").help("Total Requests.").register(c),
      headers = Histogram.build().name(prefix + "_" + "headers_times").help("Header Timings.").register(c),
      abnormalTerminations = Histogram.build().name(prefix + "_" + "abnormal_terminations").help("Abnormal Terminations.").register(c),
      serviceErrors = Histogram.build().name(prefix + "_" + "service_errors").help("Service Errors.").register(c)
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
    val responseHistograms = ResponseHistograms(
      resp1xx = Histogram.build().name(prefix + "_" + "1xx_responses").help("1xx Responses.").register(c),
      resp2xx = Histogram.build().name(prefix + "_" + "2xx_responses").help("2xx Responses.").register(c),
      resp3xx = Histogram.build().name(prefix + "_" + "3xx_responses").help("3xx Responses.").register(c),
      resp4xx = Histogram.build().name(prefix + "_" + "4xx_responses").help("4xx Responses.").register(c),
      resp5xx = Histogram.build().name(prefix + "_" + "5xx_responses").help("5xx Responses.").register(c)
    )
    val serviceMetrics = ServiceMetrics(generalServiceMetrics, requestHistograms, responseHistograms)
    
    { service: HttpService[F] => Kleisli(metricsService[F](serviceMetrics, service)(_)) }
  }

}