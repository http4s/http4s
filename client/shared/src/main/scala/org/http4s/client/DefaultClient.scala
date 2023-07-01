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

import cats.Applicative
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all._
import fs2.Stream
import org.http4s.Status.Successful
import org.http4s.headers.Accept
import org.http4s.headers.MediaRangeAndQValue

private[http4s] abstract class DefaultClient[F[_]](implicit F: MonadCancelThrow[F])
    extends Client[F] {
  def run(req: Request[F]): Resource[F, Response[F]]

  /** Returns this client as a [[cats.data.Kleisli]].  All connections created
    * by this service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `run` followed by `use`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A] =
    Kleisli(run(_).use(f))

  /** Returns this client as an [[HttpApp]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `run`, `fetchAs`,
    * [[toKleisli]] and [[stream]] signatures guarantee disposal of the
    * HTTP connection.
    */
  def toHttpApp: HttpApp[F] =
    Kleisli { req =>
      F.map(run(req).allocated) { case (resp, release) =>
        val released = resp.body.onFinalizeWeak(release)
        resp.withEntity(Entity.stream(released))
      }
    }

  def stream(req: Request[F]): Stream[F, Response[F]] =
    Stream.resource(run(req))

  def streaming[A](req: Request[F])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    stream(req).flatMap(f)

  def streaming[A](req: F[Request[F]])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    Stream.eval(req).flatMap(stream).flatMap(f)

  def expectOr[A](
      req: Request[F]
  )(onError: Response[F] => F[Throwable])(implicit d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList.map(MediaRangeAndQValue(_))
      req.addHeader(Accept(NonEmptyList.fromListUnsafe(m)))
    } else req

    run(r).use {
      case Successful(resp) =>
        d.decode(resp, strict = false).leftWiden[Throwable].rethrowT
      case failedResponse =>
        onError(failedResponse).flatMap(F.raiseError)
    }
  }

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(req)(defaultOnError(req))

  def expectOr[A](req: F[Request[F]])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A] =
    req.flatMap(expectOr(_)(onError))

  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    req.flatMap(req => expectOr(req)(defaultOnError(req)))

  def expectOr[A](uri: Uri)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A] =
    expectOr(Request[F](Method.GET, uri))(onError)

  /** Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(uri)(defaultOnError(Request[F](uri = uri)))

  def expectOr[A](s: String)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => expectOr[A](uri)(onError))

  /** Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(s)(defaultOnError(Request[F](uri = Uri.unsafeFromString(s))))

  def expectOptionOr[A](
      req: Request[F]
  )(onError: Response[F] => F[Throwable])(implicit d: EntityDecoder[F, A]): F[Option[A]] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.addHeader(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req

    run(r).use {
      case Successful(resp) =>
        d.decode(resp, strict = false).leftWiden[Throwable].rethrowT.map(_.some)
      case failedResponse =>
        failedResponse.status match {
          case Status.NotFound => Option.empty[A].pure[F]
          case Status.Gone => Option.empty[A].pure[F]
          case _ => onError(failedResponse).flatMap(F.raiseError)
        }
    }
  }

  def expectOption[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[Option[A]] =
    expectOptionOr(req)(defaultOnError(req))

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList.map(MediaRangeAndQValue(_))
      req.addHeader(Accept(NonEmptyList.fromListUnsafe(m)))
    } else req

    run(r).use { resp =>
      d.decode(resp, strict = false).leftWiden[Throwable].rethrowT
    }
  }

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    req.flatMap(fetchAs(_)(d))

  /** Submits a request and returns the response status */
  def status(req: Request[F]): F[Status] =
    run(req).use(resp => F.pure(resp.status))

  /** Submits a request and returns the response status */
  def status(req: F[Request[F]]): F[Status] =
    req.flatMap(status)

  /** Submits a GET request to the URI and returns the response status */
  override def statusFromUri(uri: Uri): F[Status] =
    status(Request[F](uri = uri))

  /** Submits a GET request to the URI and returns the response status */
  override def statusFromString(s: String): F[Status] =
    F.fromEither(Uri.fromString(s)).flatMap(statusFromUri)

  /** Submits a request and returns true if and only if the response status is
    * successful
    */
  def successful(req: Request[F]): F[Boolean] =
    status(req).map(_.isSuccess)

  /** Submits a request and returns true if and only if the response status is
    * successful
    */
  def successful(req: F[Request[F]]): F[Boolean] =
    req.flatMap(successful)

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[F] => F[A]): F[A] =
    run(Request[F](Method.GET, uri)).use(f)

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => get(uri)(f))

  def defaultOnError(req: Request[F])(resp: Response[F])(implicit
      F: Applicative[F]
  ): F[Throwable] =
    F.pure(UnexpectedStatus(resp.status, req.method, req.uri))
}
