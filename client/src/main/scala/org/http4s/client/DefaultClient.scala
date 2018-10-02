package org.http4s
package client

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import org.http4s.Status.Successful
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import scala.annotation.tailrec

private[client] abstract class DefaultClient[F[_]](implicit F: Bracket[F, Throwable])
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
    req.flatMap(fetch(_)(f))

  /**
    * Returns this client as a [[Kleisli]].  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `fetch`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A] =
    Kleisli(fetch(_)(f))

  @deprecated("Use toKleisli", "0.18")
  def toService[A](f: Response[F] => F[A]): Service[F, Request[F], A] =
    toKleisli(f)

  /**
    * Returns this client as an [[HttpApp]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `fetch`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpApp: HttpApp[F] = Kleisli { req =>
    /* Derived from https://github.com/typelevel/cats-effect/blob/c62c6c76b3066c500fca2f9e0897605bc12eb2a0/core/shared/src/main/scala/cats/effect/Resource.scala */

    // Indirection for calling `loop` needed because `loop` must be @tailrec
    def continue(
        current: Resource[F, Any],
        stack: List[Any => Resource[F, Any]],
        release: F[Unit]): F[(Any, F[Unit])] =
      loop(current, stack, release)

    // Interpreter that knows how to evaluate a Resource data structure;
    // Maintains its own stack for dealing with Bind chains
    @tailrec def loop(
        current: Resource[F, Any],
        stack: List[Any => Resource[F, Any]],
        release: F[Unit]): F[(Any, F[Unit])] =
      current match {
        case Resource.Allocate(resource) =>
          F.bracketCase(resource) {
            case (a, rel) =>
              stack match {
                case Nil => F.pure(a -> rel(ExitCase.Completed).guarantee(release))
                case f0 :: xs => continue(f0(a), xs, rel(ExitCase.Completed).guarantee(release))
              }
          } {
            case (_, ExitCase.Completed) =>
              F.unit
            case ((_, release), ec) =>
              release(ec)
          }
        case Resource.Bind(source, f0) =>
          loop(source, f0.asInstanceOf[Any => Resource[F, Any]] :: stack, release)
        case Resource.Suspend(resource) =>
          resource.flatMap(continue(_, stack, release))
      }

    loop(run(req).asInstanceOf[Resource[F, Any]], Nil, F.unit).map {
      case (a, release) =>
        val resp = a.asInstanceOf[Response[F]]
        resp.copy(body = resp.body.onFinalize(release))
    }
  }

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
  def toHttpService: HttpService[F] =
    toHttpApp.mapF(OptionT.liftF(_))

  def stream(req: Request[F]): Stream[F, Response[F]] =
    Stream.resource(run(req))

  def streaming[A](req: Request[F])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    stream(req).flatMap(f)

  def streaming[A](req: F[Request[F]])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    Stream.eval(req).flatMap(stream).flatMap(f)

  def expectOr[A](req: Request[F])(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req
    fetch(r) {
      case Successful(resp) =>
        d.decode(resp, strict = false).fold(throw _, identity)
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

  def expectOr[A](req: F[Request[F]])(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A] =
    req.flatMap(expectOr(_)(onError))

  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(req)(defaultOnError)

  def expectOr[A](uri: Uri)(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(Request[F](Method.GET, uri))(onError)

  /**
    * Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(uri)(defaultOnError)

  def expectOr[A](s: String)(onError: Response[F] => F[Throwable])(
      implicit d: EntityDecoder[F, A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => expectOr[A](uri)(onError))

  /**
    * Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
    expectOr(s)(defaultOnError)

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
    fetch(r) { resp =>
      d.decode(resp, strict = false).fold(throw _, identity)
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
    fetch(req)(resp => F.pure(resp.status))

  /** Submits a request and returns the response status */
  def status(req: F[Request[F]]): F[Status] =
    req.flatMap(status)

  /** Submits a request and returns true if and only if the response status is
    * successful */
  def successful(req: Request[F]): F[Boolean] =
    status(req).map(_.isSuccess)

  /** Submits a request and returns true if and only if the response status is
    * successful */
  def successful(req: F[Request[F]]): F[Boolean] =
    req.flatMap(successful)

  @deprecated("Use expect", "0.14")
  def prepAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] =
    fetchAs(req)(d)

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[F] => F[A]): F[A] =
    fetch(Request[F](Method.GET, uri))(f)

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => get(uri)(f))

  /**
    * Submits a GET request and decodes the response.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  @deprecated("Use expect", "0.14")
  def getAs[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] =
    fetchAs(Request[F](Method.GET, uri))(d)

  @deprecated("Use expect", "0.14")
  def getAs[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => expect[A](uri))

  @deprecated("Use expect", "0.14")
  def prepAs[T](req: F[Request[F]])(implicit d: EntityDecoder[F, T]): F[T] =
    fetchAs(req)

  private def defaultOnError(resp: Response[F])(implicit F: Applicative[F]): F[Throwable] =
    F.pure(UnexpectedStatus(resp.status))
}
