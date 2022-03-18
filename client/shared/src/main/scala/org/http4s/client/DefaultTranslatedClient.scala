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

import cats._
import cats.data.Kleisli
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all._
import fs2.Stream
import org.http4s.Status.Successful
import org.http4s.headers.Accept
import org.http4s.headers.MediaRangeAndQValue

private[http4s] class DefaultTranslatedClient[F[_], G[_]](
    client: Client[F],
    fk: F ~> G,
    gk: G ~> F,
)(implicit F: MonadCancelThrow[F], G: MonadCancelThrow[G])
    extends Client[G] {
  final def run(req: Request[G]): Resource[G, Response[G]] =
    client.run(req.mapK(gk)).mapK(fk).map(_.mapK(fk))

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is disposed when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Request[G])(f: Response[G] => G[A]): G[A] =
    fk(client.run(req.mapK(gk)).use(resp => gk(f(resp.mapK(fk)))))

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req An effect of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: G[Request[G]])(f: Response[G] => G[A]): G[A] =
    req.flatMap(req => fk(client.run(req.mapK(gk)).use(resp => gk(f(resp.mapK(fk))))))

  /** Returns this client as a [[cats.data.Kleisli]].  All connections created
    * by this service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `run` followed by `use`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[G] => G[A]): Kleisli[G, Request[G], A] =
    Kleisli(req => fk(client.run(req.mapK(gk)).use(resp => gk(f(resp.mapK(fk))))))

  /** Returns this client as an [[HttpApp]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `run`, `fetchAs`,
    * [[toKleisli]] and [[streaming]] signatures guarantee disposal of the
    * HTTP connection.
    */
  def toHttpApp: HttpApp[G] = // Might be wrong here
    Kleisli { req =>
      G.map(run(req).allocated) { case (resp, release) =>
        resp.withBodyStream(resp.body.onFinalizeWeak(release))
      }
    }

  def stream(req: Request[G]): Stream[G, Response[G]] =
    Stream.resource(run(req))

  def streaming[A](req: Request[G])(f: Response[G] => Stream[G, A]): Stream[G, A] =
    Stream
      .resource(client.run(req.mapK(gk)))
      .translate(fk)
      .flatMap(resp => f(resp.mapK(fk)).translate(gk).translate(fk))

  def streaming[A](req: G[Request[G]])(f: Response[G] => Stream[G, A]): Stream[G, A] =
    Stream.eval(req).flatMap(streaming(_)(f))

  def expectOr[A](
      req: Request[G]
  )(onError: Response[G] => G[Throwable])(implicit d: EntityDecoder[G, A]): G[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.addHeader(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req

    fk(client.run(r.mapK(gk)).use {
      case Successful(resp) =>
        gk(d.decode(resp.mapK(fk), strict = false).leftWiden[Throwable].rethrowT)
      case failedResponse =>
        gk(onError(failedResponse.mapK(fk)).flatMap(G.raiseError))
    })
  }

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[G])(implicit d: EntityDecoder[G, A]): G[A] =
    expectOr(req)(defaultOnError(req))

  def expectOr[A](req: G[Request[G]])(onError: Response[G] => G[Throwable])(implicit
      d: EntityDecoder[G, A]
  ): G[A] =
    req.flatMap(expectOr(_)(onError))

  def expect[A](req: G[Request[G]])(implicit d: EntityDecoder[G, A]): G[A] =
    req.flatMap(req => expectOr(req)(defaultOnError(req)))

  def expectOr[A](uri: Uri)(onError: Response[G] => G[Throwable])(implicit
      d: EntityDecoder[G, A]
  ): G[A] =
    expectOr[A](Request[G](Method.GET, uri))(onError)

  /** Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[G, A]): G[A] =
    expectOr(uri)(defaultOnError(Request(uri = uri)))

  def expectOr[A](s: String)(onError: Response[G] => G[Throwable])(implicit
      d: EntityDecoder[G, A]
  ): G[A] =
    Uri.fromString(s).fold(G.raiseError, uri => expectOr[A](uri)(onError))

  /** Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[G, A]): G[A] =
    expectOr[A](s)(defaultOnError(Request[G](uri = Uri.unsafeFromString(s))))

  def expectOptionOr[A](
      req: Request[G]
  )(onError: Response[G] => G[Throwable])(implicit d: EntityDecoder[G, A]): G[Option[A]] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.addHeader(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req

    fk(client.run(r.mapK(gk)).use {
      case Successful(resp) =>
        gk(d.decode(resp.mapK(fk), strict = false).leftWiden[Throwable].rethrowT.map(_.some))
      case failedResponse =>
        failedResponse.status match {
          case Status.NotFound => Option.empty[A].pure[F]
          case Status.Gone => Option.empty[A].pure[F]
          case _ => gk(onError(failedResponse.mapK(fk)).flatMap(G.raiseError))
        }
    })
  }

  def expectOption[A](req: Request[G])(implicit d: EntityDecoder[G, A]): G[Option[A]] =
    expectOptionOr(req)(defaultOnError(req))

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[G])(implicit d: EntityDecoder[G, A]): G[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.addHeader(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req

    fk(client.run(r.mapK(gk)).use { resp =>
      gk(d.decode(resp.mapK(fk), strict = false).leftWiden[Throwable].rethrowT)
    })
  }

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: G[Request[G]])(implicit d: EntityDecoder[G, A]): G[A] =
    req.flatMap(fetchAs(_)(d))

  /** Submits a request and returns the response status */
  def status(req: Request[G]): G[Status] =
    run(req).use(resp => fk(F.pure(resp.status)))

  /** Submits a request and returns the response status */
  def status(req: G[Request[G]]): G[Status] =
    req.flatMap(status)

  /** Submits a GET request to the URI and returns the response status */
  override def statusFromUri(uri: Uri): G[Status] =
    status(Request[G](uri = uri))

  /** Submits a GET request to the URI and returns the response status */
  override def statusFromString(s: String): G[Status] =
    G.fromEither(Uri.fromString(s)).flatMap(statusFromUri)

  /** Submits a request and returns true if and only if the response status is
    * successful
    */
  def successful(req: Request[G]): G[Boolean] =
    status(req).map(_.isSuccess)

  /** Submits a request and returns true if and only if the response status is
    * successful
    */
  def successful(req: G[Request[G]]): G[Boolean] =
    req.flatMap(successful)

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[G] => G[A]): G[A] =
    run(Request[G](Method.GET, uri)).use(resp => fk(gk(f(resp))))

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[G] => G[A]): G[A] =
    Uri.fromString(s).fold(G.raiseError, uri => get(uri)(f))

  private def defaultOnError(req: Request[G])(resp: Response[G])(implicit
      G: Applicative[G]
  ): G[Throwable] =
    G.pure(UnexpectedStatus(resp.status, req.method, req.uri))
}
