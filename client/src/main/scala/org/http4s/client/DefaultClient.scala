/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client

import cats.Applicative
import cats.data.Kleisli
import cats.effect.{Bracket, Resource}
import cats.implicits._
import fs2.Stream
import org.http4s.Status.Successful
import org.http4s.headers.{Accept, MediaRangeAndQValue}

private[http4s] abstract class DefaultClient[F[_]](implicit F: Bracket[F, Throwable])
    extends Client[F] {
  def run(req: Request[F]): Resource[F, Response[F]]

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is disposed when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A] =
    run(req).use(f)

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req An effect of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A] =
    req.flatMap(run(_).use(f))

  /**
    * Returns this client as a [[Kleisli]].  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `run` followed by `use`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A] =
    Kleisli(run(_).use(f))

  /**
    * Returns this client as an [[HttpApp]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `run`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpApp: HttpApp[F] =
    Kleisli { req =>
      run(req).allocated.map {
        case (resp, release) =>
          resp.withBodyStream(resp.body.onFinalizeWeak(release))
      }
    }

  def stream(req: Request[F]): Stream[F, Response[F]] =
    Stream.resource(run(req))

  def streaming[A](req: Request[F])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    stream(req).flatMap(f)

  def streaming[A](req: F[Request[F]])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    Stream.eval(req).flatMap(stream).flatMap(f)

  def expectOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req

    run(r).use {
      case Successful(resp) =>
        d.decode(resp, strict = false).leftWiden[Throwable].rethrowT
      case failedResponse =>
        onError(failedResponse).flatMap(F.raiseError)
    }
  }

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(req)(defaultOnError)

  def expectOr[A](req: F[Request[F]])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A] =
    req.flatMap(expectOr(_)(onError))

  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(req)(defaultOnError)

  def expectOr[A](uri: Uri)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A] =
    expectOr(Request[F](Method.GET, uri))(onError)

  /**
    * Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(uri)(defaultOnError)

  def expectOr[A](s: String)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => expectOr[A](uri)(onError))

  /**
    * Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(s)(defaultOnError)

  def expectOptionOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]): F[Option[A]] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
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
    expectOptionOr(req)(defaultOnError)

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req

    run(r).use { resp =>
      d.decode(resp, strict = false).leftWiden[Throwable].rethrowT
    }
  }

  /**
    * Submits a request and decodes the response, regardless of the status code.
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

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => get(uri)(f))

  private def defaultOnError(resp: Response[F])(implicit F: Applicative[F]): F[Throwable] =
    F.pure(UnexpectedStatus(resp.status))
}
