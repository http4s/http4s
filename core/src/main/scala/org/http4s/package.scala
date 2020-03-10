package org

import cats.data.{EitherT, Kleisli, OptionT}
import fs2.Stream

package object http4s { // scalastyle:ignore

  type AuthScheme = util.CaseInsensitiveString

  type EntityBody[+F[_]] = Stream[F, Byte]

  val EmptyBody: EntityBody[Nothing] = Stream.empty[Nothing]

  val ApiVersion: Http4sVersion = Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)

  type DecodeResult[F[_], A] = EitherT[F, DecodeFailure, A]

  type ParseResult[+A] = Either[ParseFailure, A]

  val DefaultCharset = Charset.`UTF-8`

  /** A kleisli with a [[Request]] input and a [[Response]] output.  This type
    * is useful for writing middleware that are polymorphic over the return
    * type F.
    *
    * @tparam F the effect type in which the [[Response]] is returned
    * @tparam G the effect type of the [[Request]] and [[Response]] bodies
    */
  type Http[F[_], G[_]] = Kleisli[F, Request[G], Response[G]]

  /** A kleisli with a [[Request]] input and a [[Response]] output, such
    * that the response effect is the same as the request and response bodies'.
    * An HTTP app is total on its inputs.  An HTTP app may be run by a server,
    * and a client can be converted to or from an HTTP app.
    *
    * @tparam F the effect type in which the [[Response]] is returned, and also
    * of the [[Request]] and [[Response]] bodies.
    */
  type HttpApp[F[_]] = Http[F, F]

  /** A kleisli with a [[Request]] input and a [[Response]] output, such
    * that the response effect is an optional inside the effect of the
    * request and response bodies.  HTTP routes can conveniently be
    * constructed from a partial function and combined as a
    * `SemigroupK`.
    *
    * @tparam F the effect type of the [[Request]] and [[Response]] bodies,
    * and the base monad of the `OptionT` in which the response is returned.
    */
  type HttpRoutes[F[_]] = Http[OptionT[F, *], F]

  @deprecated("Deprecated in favor of just using Kleisli", "0.18")
  type Service[F[_], A, B] = Kleisli[F, A, B]

  @deprecated("Deprecated in favor of HttpRoutes", "0.19")
  type HttpService[F[_]] = HttpRoutes[F]

  type AuthedRequest[F[_], T] = ContextRequest[F, T]

  /**
    * The type parameters need to be in this order to make partial unification
    * trigger. See https://github.com/http4s/http4s/issues/1506
    */
  type AuthedRoutes[T, F[_]] = Kleisli[OptionT[F, *], AuthedRequest[F, T], Response[F]]

  @deprecated("Deprecated in favor of AuthedRoutes", "0.20.1")
  type AuthedService[T, F[_]] = AuthedRoutes[T, F]

  type ContextRoutes[T, F[_]] = Kleisli[OptionT[F, *], ContextRequest[F, T], Response[F]]

  type Callback[A] = Either[Throwable, A] => Unit

  /** A stream of server-sent events */
  type EventStream[F[_]] = Stream[F, ServerSentEvent]

  @deprecated("Moved to org.http4s.syntax.AllSyntax", "0.16")
  type Http4sSyntax = syntax.AllSyntax
  @deprecated("Moved to org.http4s.syntax.all", "0.16")
  val Http4sSyntax = syntax.all
}
