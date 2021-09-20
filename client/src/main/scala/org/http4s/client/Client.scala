/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client

import cats.~>
import cats.data.Kleisli
import cats.effect._
import cats.effect.Ref
import cats.syntax.all._
import fs2._
import java.io.IOException
import org.http4s.headers.Host
import scala.util.control.NoStackTrace

/** A [[Client]] submits [[Request]]s to a server and processes the [[Response]]. */
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
  @deprecated("Use run(req).use(f)", "0.21.5")
  def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A]

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req An effect of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  @deprecated("Use req.flatMap(run(_).use(f))", "0.21.5")
  def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A]

  /** Returns this client as a [[cats.data.Kleisli]].  All connections created
    * by this service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `run` followed by `use`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A]

  /** Returns this client as an [[HttpApp]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `run`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpApp: HttpApp[F]

  /** Run the request as a stream.  The response lifecycle is equivalent
    * to the returned Stream's.
    */
  def stream(req: Request[F]): Stream[F, Response[F]]

  def expectOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A]

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](req: F[Request[F]])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A]

  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](uri: Uri)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A]

  /** Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](s: String)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A]

  /** Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A]

  def expectOptionOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[Option[A]]
  def expectOption[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[Option[A]]

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A]

  /** Submits a request and returns the response status */
  def status(req: Request[F]): F[Status]

  /** Submits a request and returns the response status */
  def status(req: F[Request[F]]): F[Status]

  /** Submits a GET request to the URI and returns the response status */
  def statusFromUri(uri: Uri): F[Status]

  /** Submits a GET request to the URI and returns the response status */
  def statusFromString(s: String): F[Status]

  /** Submits a request and returns true if and only if the response status is
    * successful
    */
  def successful(req: Request[F]): F[Boolean]

  /** Submits a request and returns true if and only if the response status is
    * successful
    */
  def successful(req: F[Request[F]]): F[Boolean]

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[F] => F[A]): F[A]

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A]

  /** Translates the effect type of this client from F to G
    */
  def translate[G[_]: Async](fk: F ~> G)(gK: G ~> F)(implicit
      F: MonadCancel[F, Throwable]): Client[G] =
    Client((req: Request[G]) =>
      run(
        req.mapK(gK)
      ).mapK(fk)
        .map(_.mapK(fk)))
}

object Client {
  def apply[F[_]](f: Request[F] => Resource[F, Response[F]])(implicit
      F: MonadCancelThrow[F]): Client[F] =
    new DefaultClient[F] {
      def run(req: Request[F]): Resource[F, Response[F]] = f(req)
    }

  /** Creates a client from the specified [[HttpApp]].  Useful for
    * generating pre-determined responses for requests in testing.
    *
    * @param app the [[HttpApp]] to respond to requests to this client
    */
  def fromHttpApp[F[_]](app: HttpApp[F])(implicit F: Async[F]): Client[F] =
    Client { (req: Request[F]) =>
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
            .eval(app(req0))
            .flatTap(_ => Resource.make(F.unit)(_ => disposed.set(true)))
            .map(resp => resp.copy(body = go(resp.body).stream))
        }
      }
    }

  /** This method introduces an important way for the effectful backends to allow tracing. As Kleisli types
    * form the backend of tracing and these transformations are non-trivial.
    */
  def liftKleisli[F[_]: MonadCancelThrow, A](client: Client[F]): Client[Kleisli[F, A, *]] =
    Client { (req: Request[Kleisli[F, A, *]]) =>
      Resource.eval(Kleisli.ask[F, A]).flatMap { a =>
        client
          .run(req.mapK(Kleisli.applyK(a)))
          .mapK(Kleisli.liftK[F, A])
          .map(_.mapK(Kleisli.liftK))
      }
    }

  private def addHostHeaderIfUriIsAbsolute[F[_]](req: Request[F]): Request[F] =
    req.uri.host match {
      case Some(host) if req.headers.get[Host].isEmpty =>
        req.withHeaders(req.headers.put(Host(host.value, req.uri.port)))
      case _ => req
    }
}

final case class UnexpectedStatus(status: Status, requestMethod: Method, requestUri: Uri)
    extends RuntimeException
    with NoStackTrace {
  override def getMessage: String =
    s"unexpected HTTP status: $status for request $requestMethod $requestUri"
}
