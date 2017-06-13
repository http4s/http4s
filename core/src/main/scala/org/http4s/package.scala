package org

import cats._
import cats.data._
import cats.effect.Sync
import fs2._

package object http4s { // scalastyle:ignore

  type AuthScheme = util.CaseInsensitiveString

  type EntityBody[+F[_]] = Stream[F, Byte]

  val EmptyBody: EntityBody[Nothing] =
    Stream.empty

  val ApiVersion: Http4sVersion =
    Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)

  type DecodeResult[F[_], A] = EitherT[F, DecodeFailure, A]

  type ParseResult[+A] = Either[ParseFailure, A]

  val DefaultCharset = Charset.`UTF-8`

  /**
   * A Service wraps a function of request type `A` to a Task that runs
   * to response type `B`.  By wrapping the [[Service]], we can compose them
   * using Kleisli operations.
   */
  type Service[F[_], A, B] = Kleisli[F, A, B]

  /**
    * A [[Service]] that produces a Task to compute a [[Response]] from a
    * [[Request]].  An HttpService can be run on any supported http4s
    * server backend, such as Blaze, Jetty, or Tomcat.
    */
  type HttpService[F[_]] = Service[F, Request[F], MaybeResponse[F]]

  type AuthedService[F[_], T] = Service[F, AuthedRequest[F, T], MaybeResponse[F]]

  /* Lives here to work around https://issues.scala-lang.org/browse/SI-7139 */
  object HttpService extends Serializable {
    /**
      * Lifts a total function to an `HttpService`. The function is expected to
      * handle all requests it is given.  If `f` is a `PartialFunction`, use
      * `apply` instead.
      */
    def lift[F[_]](f: Request[F] => F[MaybeResponse[F]]): HttpService[F] = Service.lift(f)

    /** Lifts a partial function to an `HttpService`.  Responds with
      * [[org.http4s.Response.fallthrough]], which generates a 404, for any request
      * where `pf` is not defined.
      */
    def apply[F[_]](pf: PartialFunction[Request[F], F[Response[F]]])(implicit F: Applicative[F]): HttpService[F] =
      // I don't feel good about myself
      lift(req => pf.asInstanceOf[PartialFunction[Request[F], F[MaybeResponse[F]]]].applyOrElse(req, Function.const(F.pure(Pass[F]))))

    def empty[F[_]: Sync]: HttpService[F] =
      Service.constVal(Pass[F])
  }

  object AuthedService {
    /**
      * Lifts a total function to an `HttpService`. The function is expected to
      * handle all requests it is given.  If `f` is a `PartialFunction`, use
      * `apply` instead.
      */
    def lift[F[_], T](f: AuthedRequest[F, T] => F[MaybeResponse[F]]): AuthedService[F, T] = Service.lift(f)

    /** Lifts a partial function to an `AuthedService`.  Responds with
      * [[org.http4s.Response.fallthrough]], which generates a 404, for any request
      * where `pf` is not defined.
      */
    def apply[F[_], T](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(implicit F: Applicative[F]): AuthedService[F, T] =
      // This one also makes me sick
      lift(req => pf.asInstanceOf[PartialFunction[AuthedRequest[F, T], F[MaybeResponse[F]]]].applyOrElse(req, Function.const(F.pure(Pass[F]))))

    /**
      * The empty service (all requests fallthrough).
      *
      * @tparam T - ignored.
      * @return
      */
    def empty[F[_]: Sync, T]: AuthedService[F, T] =
      Service.constVal(Pass[F])
  }

  type Callback[A] = Either[Throwable, A] => Unit

  /** A stream of server-sent events */
  type EventStream[F[_]] = Stream[F, ServerSentEvent]

  @deprecated("Moved to org.http4s.syntax.AllSyntax", "0.16")
  type Http4sSyntax = syntax.AllSyntax
  @deprecated("Moved to org.http4s.syntax.all", "0.16")
  val Http4sSyntax = syntax.all
}
