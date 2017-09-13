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
    * A Service wraps a function of request type `A` to an effect that runs
    * to response type `B`.  By wrapping the [[Service]], we can compose them
    * using Kleisli operations.
    */
  type Service[F[_], A, B] = Kleisli[F, A, B]

  /**
    * A [[Service]] that produces an effect to compute a [[Response]] from a
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
    def apply[F[_]](pf: PartialFunction[Request[F], F[Response[F]]])(
        implicit F: Applicative[F]): HttpService[F] =
      // I don't feel good about myself
      lift(
        req =>
          pf.asInstanceOf[PartialFunction[Request[F], F[MaybeResponse[F]]]]
            .applyOrElse(req, Function.const(F.pure(Pass[F]))))

    def empty[F[_]: Sync]: HttpService[F] =
      Service.constVal(Pass[F])
  }

  object AuthedService {

    /**
      * Lifts a total function to an `HttpService`. The function is expected to
      * handle all requests it is given.  If `f` is a `PartialFunction`, use
      * `apply` instead.
      */
    def lift[F[_], T](f: AuthedRequest[F, T] => F[MaybeResponse[F]]): AuthedService[F, T] =
      Service.lift(f)

    /** Lifts a partial function to an `AuthedService`.  Responds with
      * [[org.http4s.Response.fallthrough]], which generates a 404, for any request
      * where `pf` is not defined.
      */
    def apply[F[_], T](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(
        implicit F: Applicative[F]): AuthedService[F, T] =
      // This one also makes me sick
      lift(
        req =>
          pf.asInstanceOf[PartialFunction[AuthedRequest[F, T], F[MaybeResponse[F]]]]
            .applyOrElse(req, Function.const(F.pure(Pass[F]))))

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
