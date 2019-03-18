package org

import cats.data._
import fs2._

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
    * @tparam F the effect type in which the [[Response]] is returned
    */
  type HttpApp[F[_]] = Http[F, F]

  /** A kleisl with a [[Request]] input and a [[Response]] output, such
    * that the response effect is an optional inside the effect of the
    * request and response bodies.  HTTP routes can conveniently be
    * constructed from a partial function and combined as a
    * `SemigroupK`.
    *
    * @tparam F the effect type of the [[Request]] and [[Response]] bodies,
    * and the base monad of the `OptionT` in which the response is returned.
    */
  type HttpRoutes[F[_]] = Http[OptionT[F, ?], F]

  @deprecated("Deprecated in favor of just using Kleisli", "0.18")
  type Service[F[_], A, B] = Kleisli[F, A, B]

  @deprecated("Deprecated in favor of HttpRoutes", "0.19")
  type HttpService[F[_]] = HttpRoutes[F]

  /**
    * We need to change the order of type parameters to make partial unification
    * trigger. See https://github.com/http4s/http4s/issues/1506
    */
  type AuthedService[T, F[_]] = Kleisli[OptionT[F, ?], AuthedRequest[F, T], Response[F]]

  type Callback[A] = Either[Throwable, A] => Unit

  /** A stream of server-sent events */
  type EventStream[F[_]] = Stream[F, ServerSentEvent]

  @deprecated("Moved to org.http4s.syntax.AllSyntax", "0.16")
  type Http4sSyntax = syntax.AllSyntax
  @deprecated("Moved to org.http4s.syntax.all", "0.16")
  val Http4sSyntax = syntax.all

  // Adapted from fs2: https://github.com/functional-streams-for-scala/fs2/blob/923c87d87a48001e7a45128f7184410cb9972283/core/shared/src/main/scala/fs2/fs2.scala#L25-L40
  // Trick to get right-biased syntax for Either in 2.11 while retaining source compatibility with 2.12 and leaving
  // -Xfatal-warnings and -Xwarn-unused-imports enabled. Delete when no longer supporting 2.11.
  private[http4s] implicit class EitherSyntax[L, R](private val self: Either[L, R]) extends AnyVal {
    def map[R2](f: R => R2): Either[L, R2] = self match {
      case Right(r) => Right(f(r))
      case l @ Left(_) => l.asInstanceOf[Either[L, R2]]
    }

    def flatMap[R2](f: R => Either[L, R2]): Either[L, R2] = self match {
      case Right(r) => f(r)
      case l @ Left(_) => l.asInstanceOf[Either[L, R2]]
    }

    def toOption: Option[R] = self match {
      case Right(r) => Some(r)
      case Left(_) => None
    }

    def getOrElse[R2 >: R](default: => R2): R2 = self match {
      case Right(r) => r
      case Left(_) => default
    }

    def valueOr[R2 >: R](f: L => R2): R2 = self match {
      case Right(r) => r
      case Left(l) => f(l)
    }
  }
}
