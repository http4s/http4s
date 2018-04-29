package org.http4s.server.play

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.data.OptionT
import cats.effect.{Effect, IO}
import fs2.interop.reactivestreams._
import org.http4s.{EmptyBody, Header, Headers, HttpService, Method, Request, Response, Uri}
import play.api.http.HttpEntity.Streamed
import play.api.libs.streams.Accumulator
import play.api.mvc._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class PlayRouteBuilder[F[_]](
    service: HttpService[F]
)(implicit F: Effect[F], executionContext: ExecutionContext) {

  type UnwrappedKleisli = Request[F] => OptionT[F, Response[F]]
  private[this] val unwrappedRun: UnwrappedKleisli = service.run

  def requestHeaderToRequest(requestHeader: RequestHeader): Request[F] =
    Request(
      method = Method.fromString(requestHeader.method).getOrElse(???),
      uri = Uri(path = requestHeader.uri),
      headers = Headers.apply(requestHeader.headers.toMap.toList.flatMap {
        case (headerName, values) =>
          values.map { value =>
            Header(headerName, value)
          }
      }),
      body = EmptyBody
    )

  type SinkType = Sink[ByteString, Future[Result]]
  type PlayAccumulator = Accumulator[ByteString, Result]

  type ResponseStream = fs2.Stream[F, Byte]
  type PlayTargetStream = Source[ByteString, _]

  def convertStream(responseStream: ResponseStream): PlayTargetStream = {
    val entityBody: fs2.Stream[F, Byte] = responseStream
    Source
      .fromPublisher(entityBody.toUnicastPublisher())
      .map(byte => ByteString(byte))

  }

  def playRequestToPlayResponse(requestHeader: RequestHeader): PlayAccumulator = {
    val sink: Sink[ByteString, Future[Result]] = {
      Sink.asPublisher[ByteString](false).mapMaterializedValue { publisher =>
        val bodyStream: fs2.Stream[F, Byte] =
          publisher.toStream().flatMap(bs => fs2.Stream.fromIterator[F, Byte](bs.toIterator))

        val wrappedResponse: F[Response[F]] =
          F.map(
            unwrappedRun(requestHeaderToRequest(requestHeader).withBodyStream(bodyStream)).value)(
            _.get)
        val wrappedResult: F[Result] = F.map(wrappedResponse) { response =>
          Result(
            header = convertResponseToHeader(response),
            body = Streamed(
              data = convertStream(response.body),
              contentLength = response.contentLength,
              contentType = response.contentType.map(_.value)
            )
          )
        }

        val promise = Promise[Result]

        F.runAsync(wrappedResult) {
            case Left(bad) =>
              promise.failure(bad)
              IO.unit
            case Right(good) =>
              promise.success(good)
              IO.unit
          }
          .unsafeRunSync()

        promise.future
      }
    }
    Accumulator.apply(sink)
  }

  def convertResponseToHeader(response: Response[F]): ResponseHeader =
    ResponseHeader(
      status = response.status.code,
      headers = response.headers.collect {
        case header
            if !PlayRouteBuilder.AkkaHttpSetsSeparately.contains(header.parsed.name.value) =>
          header.parsed.name.value -> header.parsed.value
      }.toMap
    )

  /** Big big hack :-( Teach me! **/
  def routeMatches(requestHeader: RequestHeader): Boolean = {
    val optionalResponse: OptionT[F, Response[F]] =
      unwrappedRun.apply(requestHeaderToRequest(requestHeader))
    val efff: F[Option[Response[F]]] = optionalResponse.value
    val completion = Promise[Boolean]()
    F.runAsync(efff) {
        case Left(f) => completion.failure(f); IO.unit
        case Right(s) => completion.success(s.isDefined); IO.unit
      }
      .unsafeRunSync()
    Await.result(completion.future, Duration.Inf)
  }

  def build: _root_.play.api.routing.Router.Routes = {
    case requestHeader if routeMatches(requestHeader) =>
      EssentialAction(playRequestToPlayResponse)
  }

}

object PlayRouteBuilder {

  /** Borrowed from Play for now **/
  def withPrefix(
      prefix: String,
      t: _root_.play.api.routing.Router.Routes): _root_.play.api.routing.Router.Routes =
    if (prefix == "/") {
      t
    } else {
      val p = if (prefix.endsWith("/")) prefix else prefix + "/"
      val prefixed: PartialFunction[RequestHeader, RequestHeader] = {
        case rh: RequestHeader if rh.path.startsWith(p) =>
          val newPath = rh.path.drop(p.length - 1)
          rh.withTarget(rh.target.withPath(newPath))
      }
      Function.unlift(prefixed.lift.andThen(_.flatMap(t.lift)))
    }

  val AkkaHttpSetsSeparately = Set("Content-Type", "Content-Length", "Transfer-Encoding")

}
