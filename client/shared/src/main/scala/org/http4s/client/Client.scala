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

import cats.data._
import cats.effect._
import cats.effect.implicits.genSpawnOps
import cats.effect.implicits.monadCancelOps_
import cats.effect.kernel.CancelScope
import cats.effect.kernel.Poll
import cats.effect.kernel.Resource
import cats.syntax.all._
import cats.~>
import fs2._
import fs2.concurrent.Channel
import org.http4s.headers.Host

import java.io.IOException
import scala.util.control.NoStackTrace

/** A [[Client]] submits [[Request]]s to a server and processes the [[Response]].
  *
  * When a connection is "released" and the HTTP semantics of the
  * request and response permit, the connection may be kept alive by
  * the backend and used for a subsequent request.  When HTTP
  * semantics require it, or at the backend's discretion, a released
  * connection may also be closed.
  */
trait Client[F[_]] {
  def run(req: Request[F]): Resource[F, Response[F]]

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is released when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  @deprecated("Use run(req).use(f)", "0.21.5")
  def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A]

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req An effect of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is released when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  @deprecated("Use req.flatMap(run(_).use(f))", "0.21.5")
  def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A]

  /** Returns this client as a [[cats.data.Kleisli]].  All connections created
    * by this service are released on completion of callback task f.
    *
    * This method effectively reverses the arguments to `run` followed by `use`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A]

  /** Returns this client as an [[HttpApp]].  It is the responsibility
    * of callers of this service to run the response body to release
    * the underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  [[run]], [[fetchAs[A](req:org\.http4s\.Request[F])*]],
    * [[toKleisli]], and [[stream]] are safer alternatives, as their
    * signatures guarantee release of the HTTP connection.
    */
  def toHttpApp: HttpApp[F]

  /** Run the request as a stream.  The response lifecycle is equivalent
    * to the returned Stream's.
    */
  def stream(req: Request[F]): Stream[F, Response[F]]

  def expectOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A]

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is released at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](req: F[Request[F]])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A]

  @deprecated("Use req.flatMap(expect(_))", "0.23.16")
  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](uri: Uri)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A]

  /** Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is released at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A]

  def expectOr[A](s: String)(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[A]

  /** Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is released at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A]

  def expectOptionOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): F[Option[A]]

  def expectOption[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[Option[A]]

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is released at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A]

  /** Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is released at the completion of the
    * decoding.
    */
  @deprecated("Use req.flatMap(fetchAs(_))", "0.23.16")
  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A]

  /** Submits a request and returns the response status */
  def status(req: Request[F]): F[Status]

  /** Submits a request and returns the response status */
  @deprecated("Use req.flatMap(status(_))", "0.23.16")
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
  @deprecated("Use req.flatMap(successful(_))", "0.23.16")
  def successful(req: F[Request[F]]): F[Boolean]

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is released when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[F] => F[A]): F[A]

  /** Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is released at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A]

  /** As [[#expectOptionOr]], but defined in terms of [[cats.data.OptionT]]. */
  final def expectOptionOrT[A](req: Request[F])(onError: Response[F] => F[Throwable])(implicit
      d: EntityDecoder[F, A]
  ): OptionT[F, A] =
    OptionT(expectOptionOr(req)(onError)(d))

  /** As [[#expectOption]], but defined in terms of [[cats.data.OptionT]]. */
  final def expectOptionT[A](req: Request[F])(implicit d: EntityDecoder[F, A]): OptionT[F, A] =
    OptionT(expectOption[A](req)(d))

  @deprecated("use public method with MonadCancelThrow instead", since = "0.23.7")
  private[client] def translate[G[_]: Async](
      fk: F ~> G
  )(gK: G ~> F)(implicit F: MonadCancelThrow[F]): Client[G] = translateImpl(fk)(gK)

  /** Translates the effect type of this client from F to G
    */
  @deprecated("use public method without MonadCancelThrow[G] constraint instead", since = "0.23.12")
  private[client] def translate[G[_]: MonadCancelThrow](
      fk: F ~> G
  )(gK: G ~> F)(implicit F: MonadCancelThrow[F]): Client[G] = translateImpl(fk)(gK)

  /** Translates the effect type of this client from F to G
    */
  def translate[G[_]](
      fk: F ~> G
  )(gK: G ~> F)(implicit F: MonadCancelThrow[F]): Client[G] = translateImpl(fk)(gK)

  private[client] def translateImpl[G[_]](
      fk: F ~> G
  )(gK: G ~> F)(implicit F: MonadCancelThrow[F]): Client[G] = {
    implicit val G: MonadCancelThrow[G] = liftMonadCancel(F)(fk)(gK)
    Client[G]((req: Request[G]) =>
      run(
        req.mapK(gK)
      ).mapK(fk)
        .map(_.mapK(fk))
    )
  }

  private[this] def liftMonadCancel[G[_]](
      F: MonadCancelThrow[F]
  )(fk: F ~> G)(gk: G ~> F): MonadCancelThrow[G] =
    new MonadCancelThrow[G] {
      def pure[A](x: A): G[A] = fk(F.pure(x))

      // Members declared in cats.ApplicativeError
      def handleErrorWith[A](ga: G[A])(f: Throwable => G[A]): G[A] =
        fk(F.handleErrorWith(gk(ga))(ex => gk(f(ex))))

      def raiseError[A](e: Throwable): G[A] = fk(F.raiseError[A](e))

      // Members declared in cats.FlatMap
      def flatMap[A, B](ga: G[A])(f: A => G[B]): G[B] =
        fk(F.flatMap(gk(ga))(a => gk(f(a))))

      def tailRecM[A, B](a: A)(f: A => G[Either[A, B]]): G[B] =
        fk(F.tailRecM(a)(a => gk(f(a))))

      // Members declared in cats.effect.kernel.MonadCancel
      def canceled: G[Unit] = fk(F.canceled)

      def forceR[A, B](ga: G[A])(gb: G[B]): G[B] =
        fk(F.forceR(gk(ga))(gk(gb)))

      def onCancel[A](ga: G[A], fin: G[Unit]): G[A] =
        fk(F.onCancel(gk(ga), gk(fin)))

      def rootCancelScope: CancelScope = F.rootCancelScope

      def uncancelable[A](body: Poll[G] => G[A]): G[A] =
        fk(F.uncancelable { pollF =>
          gk(body(new Poll[G] {
            def apply[B](gb: G[B]): G[B] = fk(pollF(gk(gb)))
          }))
        })
    }
}

object Client {
  def apply[F[_]](
      f: Request[F] => Resource[F, Response[F]]
  )(implicit F: MonadCancelThrow[F]): Client[F] =
    new DefaultClient[F] {
      def run(req: Request[F]): Resource[F, Response[F]] = f(req)
    }

  /** Creates a client from the specified [[HttpApp]].  Useful for
    * generating pre-determined responses for requests in testing.
    *
    * @param app the [[HttpApp]] to respond to requests to this client
    */
  def fromHttpApp[F[_]](app: HttpApp[F])(implicit F: Async[F]): Client[F] = {
    def until[A](disposed: Ref[F, Boolean])(source: Stream[F, A]): Stream[F, A] = {
      def go(stream: Stream[F, A]): Pull[F, A, Unit] =
        stream.pull.uncons.flatMap {
          case Some((chunk, stream)) =>
            Pull.eval(disposed.get).flatMap {
              case true => Pull.raiseError[F](new IOException("response was disposed"))
              case false => Pull.output(chunk) >> go(stream)
            }
          case None => Pull.done
        }
      go(source).stream
    }

    def processResponse(
        response: Response[F],
        disposed: Ref[F, Boolean],
    ): Resource[F, Response[F]] =
      for {
        channel <- Resource.eval(Channel.synchronous[F, Chunk[Byte]])

        producer = response.body.chunks
          .through(channel.sendAll)
          .compile
          .drain
          .uncancelable

        _ <- Resource.make(producer.start)(p => channel.stream.compile.drain.guarantee(p.join.void))

        r = response.withBodyStream(
          Stream
            .eval(disposed.get)
            .ifM(
              Stream.raiseError[F](new IOException("response was disposed")),
              channel.stream.unchunks
                .onFinalize(
                  channel.stream.compile.drain
                    .guarantee(disposed.set(true))
                ),
            )
        )

        _ <- Resource.onFinalize {
          disposed.get
            .ifM(
              F.unit,
              r.body.compile.drain,
            )
        }

      } yield r

    def run(req: Request[F]): Resource[F, Response[F]] =
      Resource.eval(Ref[F].of(false)).flatMap { disposed =>
        val reqAugmented =
          addHostHeaderIfUriIsAbsolute(req.pipeBodyThrough(until(disposed)))
        Resource
          .eval(app(reqAugmented))
          .onFinalize(disposed.set(true))
          .flatMap(processResponse(_, disposed))
      }

    Client(run)
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
      case Some(host) if !req.headers.contains[Host] =>
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
