package org.http4s.server.play

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.data.OptionT
import cats.effect.Effect
import org.http4s.{EmptyBody, Header, Headers, HttpService, Method, Request, Response, Uri}
import play.api.http.HttpEntity.Streamed
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction, RequestHeader, ResponseHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

class PlayRouteBuilder[F[_]](
    service: HttpService[Future]
)(implicit F: Effect[Future], executionContext: ExecutionContext) {

  //noinspection ConvertExpressionToSAM
  def f: EssentialAction = new EssentialAction {
    override def apply(v1: RequestHeader): Accumulator[ByteString, Result] = ???
  }

  type R = Request[Future] => OptionT[Future, Response[Future]]
  private[this] val r: R = service.run

  def requestHeaderToRequest(requestHeader: RequestHeader): Request[Future] =
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

  def resulter(requestHeader: RequestHeader): Accumulator[ByteString, Result] = {

    val res = r.apply(requestHeaderToRequest(requestHeader)).value
    val rf = res.map(_.get)
    Accumulator.done(rf.map { response =>
      Result(
        header = ResponseHeader(
          status = response.status.code,
          headers = response.headers.map { header =>
            header.parsed.name.value -> header.parsed.value
          }.toMap
        ),
        body = {
          val entityBody: fs2.Stream[Future, Byte] = response.body
          type PlayTarget = Source[ByteString, _]
          import fs2.interop.reactivestreams._

          val playBody: PlayTarget =
            Source.fromPublisher(entityBody.toUnicastPublisher()).map(byte => ByteString(byte))

          Streamed(
            data = playBody,
            contentLength = response.contentLength,
            contentType = response.contentType.map(_.value)
          )
        }
      )

    })
//    val sink: SinkType = res
//
//    Accumulator.apply(??? : SinkType)
  }

  /**
    * Play's route matching is synchronous so must await for the future, effectively... :-(
    */
  def build: _root_.play.api.routing.Router.Routes = {
    case something if r.apply(requestHeaderToRequest(something)).value.value.get.get.isDefined =>
      new EssentialAction {
        override def apply(v1: RequestHeader): Accumulator[ByteString, Result] =
          resulter(v1)
      }
//    type HttpService[F[_]] = Kleisli[OptionT[F, ?], Request[F], Response[F]]
  }

}
