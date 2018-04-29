package org.http4s.server.play

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.data.OptionT
import cats.effect.{Effect, IO}
import org.http4s.{EmptyBody, Header, Headers, HttpService, Method, Request, Response, Uri}
import play.api.http.HttpEntity.Streamed
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction, RequestHeader, ResponseHeader, Result}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class PlayRouteBuilder[F[_]](
    service: HttpService[F]
)(implicit F: Effect[F], executionContext: ExecutionContext) {
  private val FE = Effect[F]

  FE.delay(0)

  type R = Request[F] => OptionT[F, Response[F]]
  private[this] val r: R = service.run

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

  def playRequestToPlayResponse(requestHeader: RequestHeader): Accumulator[ByteString, Result] = {
    val http4sResponse: F[Option[Response[F]]] =
      r.apply(requestHeaderToRequest(requestHeader)).value
    // I know, ugly, will fix once I get it to work
    val http4sResponseExists: F[Response[F]] = F.map(http4sResponse)(_.get)
    val resultContainer: F[Result] = F.map(http4sResponseExists) { response =>
      Result(
        header = ResponseHeader(
          status = response.status.code,
          headers = response.headers.map { header =>
            header.parsed.name.value -> header.parsed.value
          }.toMap
        ),
        body = {
          val entityBody: fs2.Stream[F, Byte] = response.body
          type PlayTarget = Source[ByteString, _]
          import fs2.interop.reactivestreams._

          // hack!
          val playBody: PlayTarget =
            Source.fromPublisher(entityBody.toUnicastPublisher()).map(byte => ByteString(byte))

          Streamed(
            data = playBody,
            contentLength = response.contentLength,
            contentType = response.contentType.map(_.value)
          )
        }
      )

    }

    // hack
    val promise = Promise[Result]

    F.runAsync(resultContainer) {
      case Left(bad) =>
        promise.failure(bad)
        IO.unit
      case Right(good) =>
        promise.success(good)
        IO.unit
    }
    Accumulator.done(promise.future)
  }

  /**
    * Play's route matching is synchronous so must await for the future, effectively... :-(
    */
  def build: _root_.play.api.routing.Router.Routes = {
    case something if {
          val part: OptionT[F, Response[F]] = r.apply(requestHeaderToRequest(something))
          val efff: F[Option[Response[F]]] = part.value
          val completion = Promise[Boolean]()
          FE.runAsync(efff) {
              case Left(f) => completion.failure(f); IO.unit
              case Right(s) => completion.success(s.isDefined); IO.unit
            }
            .unsafeRunSync()
          Await.result(completion.future, Duration.Inf)

        } =>
      new EssentialAction {
        override def apply(v1: RequestHeader): Accumulator[ByteString, Result] =
          playRequestToPlayResponse(v1)
      }
//    type HttpService[F[_]] = Kleisli[OptionT[F, ?], Request[F], Response[F]]
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
}
