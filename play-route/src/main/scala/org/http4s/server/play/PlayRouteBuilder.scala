package org.http4s.server.play

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.data.OptionT
import cats.effect.{Async, Effect, IO}
import fs2.Chunk
import fs2.interop.reactivestreams._
import org.http4s.server.play.PlayRouteBuilder.{PlayAccumulator, PlayRouting, PlayTargetStream}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{
  EmptyBody,
  EntityBody,
  Header,
  Headers,
  HttpService,
  Method,
  Request,
  Response,
  Uri
}
import play.api.http.HttpEntity.Streamed
import play.api.libs.streams.Accumulator
import play.api.mvc._
import cats.syntax.all._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class PlayRouteBuilder[F[_]](
    service: HttpService[F]
)(implicit F: Effect[F], executionContext: ExecutionContext) {

  type UnwrappedKleisli = Request[F] => OptionT[F, Response[F]]
  private[this] val unwrappedRun: UnwrappedKleisli = service.run

  def requestHeaderToRequest(requestHeader: RequestHeader, method: Method): Request[F] =
    Request(
      // todo fix the ???
      method = method,
      uri = Uri(path = requestHeader.uri),
      headers = Headers.apply(requestHeader.headers.toMap.toList.flatMap {
        case (headerName, values) =>
          values.map { value =>
            Header(headerName, value)
          }
      }),
      body = EmptyBody
    )

  def convertStream(responseStream: EntityBody[F]): PlayTargetStream = {
    val entityBody: fs2.Stream[F, ByteString] =
      responseStream.chunks.map(chunk => ByteString(chunk.toArray))
    Source
      .fromPublisher(entityBody.toUnicastPublisher())
  }

  def effectToFuture[T](eff: F[T]): Future[T] = {
    val promise = Promise[T]
    F.runAsync(eff) {
        case Left(bad) =>
          IO(promise.failure(bad))
        case Right(good) =>
          IO(promise.success(good))
      }
      .unsafeRunSync()

    promise.future
  }

  /**
    * A Play accumulator Sinks HTTP data in, and then pumps out a future of a Result.
    * That Result will have a Source as the response HTTP Entity.
    *
    * Here we create a unattached sink, map its materialized value into a publisher,
    * convert that into an FS2 Stream, then pipe the request body into the http4s request.
    */
  def playRequestToPlayResponse(requestHeader: RequestHeader, method: Method): PlayAccumulator = {
    val sink: Sink[ByteString, Future[Result]] = {
      Sink.asPublisher[ByteString](fanout = false).mapMaterializedValue { publisher =>
        val requestBodyStream: fs2.Stream[F, Byte] =
          publisher.toStream().flatMap(bs => fs2.Stream.chunk(Chunk.bytes(bs.toArray)))

        val http4sRequest =
          requestHeaderToRequest(requestHeader, method).withBodyStream(requestBodyStream)

        /** The .get here is safe because this was already proven in the pattern match of the caller **/
        val wrappedResponse: F[Response[F]] = unwrappedRun(http4sRequest).value.map(_.get)
        val wrappedResult: F[Result] = wrappedResponse.map { response =>
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

        F.runAsync(Async.shift(executionContext) *> wrappedResult) {
            case Left(bad) =>
              IO(promise.failure(bad))
            case Right(good) =>
              IO(promise.success(good))
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
        case header if !PlayRouteBuilder.AkkaHttpSetsSeparately.contains(header.name) =>
          header.parsed.name.value -> header.parsed.value
      }.toMap
    )

  def routeMatches(requestHeader: RequestHeader, method: Method): Boolean = {
    val optionalResponse: OptionT[F, Response[F]] =
      unwrappedRun.apply(requestHeaderToRequest(requestHeader, method))
    val efff: F[Option[Response[F]]] = optionalResponse.value
    val completion = Promise[Boolean]()
    F.runAsync(Async.shift(executionContext) *> efff) {
        case Left(f) => IO(completion.failure(f))
        case Right(s) => IO(completion.success(s.isDefined))
      }
      .unsafeRunSync()
    Await.result(completion.future, Duration.Inf)
  }

  def build: PlayRouting = {
    case requestHeader
        if Method.fromString(requestHeader.method).isRight && routeMatches(
          requestHeader,
          Method.fromString(requestHeader.method).right.get) =>
      EssentialAction(
        playRequestToPlayResponse(_, Method.fromString(requestHeader.method).right.get))
  }

}

object PlayRouteBuilder {

  type PlayRouting = PartialFunction[RequestHeader, Handler]

  type PlayAccumulator = Accumulator[ByteString, Result]

  type PlayTargetStream = Source[ByteString, _]

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

  val AkkaHttpSetsSeparately: Set[CaseInsensitiveString] =
    Set("Content-Type", "Content-Length", "Transfer-Encoding").map(CaseInsensitiveString.apply)

}
