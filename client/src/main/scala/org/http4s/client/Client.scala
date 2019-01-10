package org.http4s
package client

import cats.data.Kleisli
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import java.io.IOException
import org.http4s.headers.Host
import org.http4s.syntax.kleisli._
import scala.util.control.NoStackTrace

/**
  * A [[Client]] submits [[Request]]s to a server and processes the [[Response]].
  *
  * @param open a service to asynchronously return a [[DisposableResponse]] from
  *             a [[Request]].  This is a low-level operation intended for client
  *             implementations and middleware.
  *
  * @param shutdown an effect to shut down this client, closing any
  *                 open connections and freeing resources
  */
trait Client[F[_]] {
  def run(req: Request[F]): Resource[F, Response[F]]

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is disposed when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A]

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req An effect of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A]

  /**
    * Returns this client as a [[Kleisli]].  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `fetch`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A]

  @deprecated("Use toKleisli", "0.18")
  def toService[A](f: Response[F] => F[A]): Service[F, Request[F], A]

  /**
    * Returns this client as an [[HttpApp]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `fetch`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpApp: HttpApp[F]

  /**
    * Returns this client as an [[HttpService]].  It is the
    * responsibility of callers of this service to run the response
    * body to dispose of the underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `fetch`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  @deprecated("Use toHttpApp. Call `.mapF(OptionT.liftF)` if OptionT is really desired.", "0.19")
  def toHttpService: HttpService[F]

  /** Run the request as a stream.  The response lifecycle is equivalent
    * to the returned Stream's. */
  def stream(req: Request[F]): Stream[F, Response[F]]

  @deprecated("Use `client.stream(req).flatMap(f)`", "0.19.0-M4")
  def streaming[A](req: Request[F])(f: Response[F] => Stream[F, A]): Stream[F, A]

  @deprecated("Use `Stream.eval(req).flatMap(client.stream).flatMap(f)`", "0.19.0-M4")
  def streaming[A](req: F[Request[F]])(f: Response[F] => Stream[F, A]): Stream[F, A]

  def expectOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A]

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](req: F[Request[F]])(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A]

  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](uri: Uri)(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A]

  /**
    * Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](s: String)(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A]

  /**
    * Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A]

  def expectOptionOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[Option[A]]
  def expectOption[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[Option[A]]

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A]

  /** Submits a request and returns the response status */
  def status(req: Request[F]): F[Status]

  /** Submits a request and returns the response status */
  def status(req: F[Request[F]]): F[Status]

  /** Submits a request and returns true if and only if the response status is
    * successful */
  def successful(req: Request[F]): F[Boolean]

  /** Submits a request and returns true if and only if the response status is
    * successful */
  def successful(req: F[Request[F]]): F[Boolean]

  @deprecated("Use expect", "0.14")
  def prepAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[F] => F[A]): F[A]

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A]

  /**
    * Submits a GET request and decodes the response.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  @deprecated("Use expect", "0.14")
  def getAs[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A]

  @deprecated("Use expect", "0.14")
  def getAs[A](s: String)(implicit d: EntityDecoder[F, A]): F[A]

  @deprecated("Use expect", "0.14")
  def prepAs[T](req: F[Request[F]])(implicit d: EntityDecoder[F, T]): F[T]
}

object Client {
  def apply[F[_]](f: Request[F] => Resource[F, Response[F]])(
      implicit F: Bracket[F, Throwable]): Client[F] = new DefaultClient[F] {
    def run(req: Request[F]): Resource[F, Response[F]] = f(req)
  }

  /** Creates a client from the specified service.  Useful for generating
    * pre-determined responses for requests in testing.
    *
    * @param service the service to respond to requests to this client
    */
  @deprecated("Use fromHttpApp instead. Call service.orNotFound to turn into an HttpApp.", "0.19")
  def fromHttpService[F[_]](service: HttpRoutes[F])(implicit F: Sync[F]): Client[F] =
    fromHttpApp(service.orNotFound)

  /** Creates a client from the specified [[HttpApp]].  Useful for
    * generating pre-determined responses for requests in testing.
    *
    * @param app the [[HttpApp]] to respond to requests to this client
    */
  def fromHttpApp[F[_]](app: HttpApp[F])(implicit F: Sync[F]): Client[F] =
    Client { req: Request[F] =>
      Resource.suspend {
        Ref[F].of(false).map { disposed =>
          def go(stream: Stream[F, Byte]): Pull[F, Byte, Unit] =
            stream.pull.uncons.flatMap {
              case Some((chunk, stream)) =>
                Pull.eval(disposed.get).flatMap {
                  case true =>
                    Pull.raiseError[F](new IOException("response was disposed"))
                  case false =>
                    Pull.output(chunk) >> go(stream)
                }
              case None =>
                Pull.done
            }
          val req0 =
            addHostHeaderIfUriIsAbsolute(req.withBodyStream(go(req.body).stream))
          Resource
            .make(app(req0))(_ => disposed.set(true))
            .map(resp => resp.copy(body = go(resp.body).stream))
        }
      }
    }

  private def addHostHeaderIfUriIsAbsolute[F[_]](req: Request[F]): Request[F] =
    req.uri.host match {
      case Some(host) if req.headers.get(Host).isEmpty =>
        req.withHeaders(req.headers.put(Host(host.value, req.uri.port)))
      case _ => req
    }
}

final case class UnexpectedStatus(status: Status) extends RuntimeException with NoStackTrace {
  override def getMessage: String = s"unexpected HTTP status: $status"
}
