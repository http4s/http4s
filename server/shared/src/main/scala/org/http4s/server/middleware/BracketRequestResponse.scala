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

package org.http4s.server.middleware

import cats.Applicative
import cats.data._
import cats.effect._
import cats.effect.kernel.Resource.ExitCase
import cats.effect.syntax.all._
import cats.implicits._
import org.http4s._
import org.http4s.server._

/** Middlewares which allow for bracketing on a Request/Response, including
  * the completion of the Response body stream.
  *
  * These are analogous to `cats.effect.Bracket` and `fs2.Stream.bracket`. The
  * reason that they exist is because due to the full termination of a
  * Response being a function of the termination of the `fs2.Stream` which
  * backs the response body, you can't actually use either
  * `cats.effect.Bracket` or `fs2.Stream.bracket` directly.
  *
  * @define releaseWarning The bracketing semantics defined here differ in one
  *         very important way from cats-effect or fs2 bracketing
  *         semantics. If the [[Response]] body is not evaluated after the
  *         application of the middleware, then the `release` handler ''will
  *         not run, effectively creating a resource leak.'' This can happen
  *         due to a bug in a server implementation, where it fails to drain
  *         the body, or due to a user code if the [[Response]] body stream
  *         (or the [[Response]] itself) is discarded after the application of
  *         this middleware. Unfortunately, there is currently no way to avoid
  *         this. For this reason, it is strongly recommended that this
  *         middleware be applied at the most outer scope possible. Appending
  *         to or transforming the [[Response]] body ''is safe'' to do after
  *         the application of this middleware.
  */
object BracketRequestResponse {

  /** A Middleware which uses both a [[ContextRequest]] and [[ContextResponse]]. */
  type FullContextMiddleware[F[_], A, B] =
    Kleisli[OptionT[F, *], ContextRequest[F, A], ContextResponse[F, B]] => Kleisli[
      OptionT[F, *],
      Request[F],
      Response[F],
    ]

  /** Bracket on the start of a request and the completion of processing the
    * response ''body Stream''.
    *
    * This middleware uses a context on both the Request and Response. This is
    * the most general possible encoding for bracketing on a Request/Response
    * interaction. It is required to express certain types of bracketing use
    * cases (see Metrics), but for most cases it is more general than is
    * needed. Before attempting to use this middleware, you should consider if
    * [[bracketRequestResponseRoutes]] or
    * [[bracketRequestResponseCaseRoutes]] will work for your use case.
    *
    * @note $releaseWarning
    *
    * @note For some use cases, you might want to differentiate between each
    *       of the three exit branches where `release` may be invoked. The
    *       three exit branches are, running a [[Request]] and receiving
    *       `OptionT.none[F, Response[F]]`, running a [[Request]] and
    *       encountering an error or cancellation before the [[Response]] is
    *       generated, or at the full consumption of the [[Response]] body
    *       stream (regardless of the `ExitCase`). One may determine the in
    *       which branch `release` is being invoked by inspecting the
    *       `ExitCase` and the `Option[B]` response context. If the response
    *       context is defined, e.g. `Some(_: B)`, then you can be certain
    *       that the `release` function was executed at the termination of the
    *       body stream. This is because the response context is only (and
    *       always) present if the [[Response]] value was generated
    *       successfully (whether or not the body stream fails). If the
    *       `ExitCase` is `Completed` and the response context is `None`, then
    *       that means that the underlying [[HttpRoutes]] did not yield a
    *       [[Response]], e.g. we got `OptionT.none: OptionT[F, Response[F]]`.
    *       Otherwise, if the `ExitCase` is either `Error(_: Throwable)` or
    *       `Canceled`, and the response context is `None`, then `release` is
    *       executed in response to an error or cancellation during the
    *       generation of the [[Response]] value proper, e.g. when running the
    *       underlying `HttpRoutes`.
    *
    * @note A careful reader might be wondering where the analogous `use`
    *       parameter from `cats.effect.Bracket` has gone. The use of the
    *       acquired resource is running the request, thus the `use` function
    *       is actually just the normal context route function from http4s
    *       `Kleisli[OptionT[F, *], ContextRequest[F, A], Response[F]]`.
    *
    * @param acquire Function of the [[Request]] to a `F[ContextRequest]` to
    *        run each time a new [[Request]] is received.
    *
    * @param release Function to run on the termination of a Request/Response
    *        interaction, in all cases. The first argument is the request
    *        context, which is guaranteed to exist if acquire succeeds. The
    *        second argument is the response context, which will only exist if
    *        the generation of the `ContextResponse` is successful. The third
    *        argument is the exit condition, either completed, canceled, or
    *        error.
    */
  def bracketRequestResponseCaseRoutes_[F[_], A, B](
      acquire: Request[F] => F[ContextRequest[F, A]]
  )(release: (A, Option[B], Outcome[F, Throwable, Unit]) => F[Unit])(
      implicit // TODO: Maybe we can merge A and Outcome
      F: MonadCancelThrow[F]
  ): FullContextMiddleware[F, A, B] =
    // format: off
    (bracketRoutes: Kleisli[OptionT[F, *], ContextRequest[F, A], ContextResponse[F, B]]) =>
      Kleisli((request: Request[F]) =>
        OptionT(
          acquire(request).flatMap(contextRequest =>
            bracketRoutes(contextRequest)
              .foldF(release(contextRequest.context, None, Outcome.succeeded(F.unit)) *> F.pure(
                None: Option[Response[F]]))(contextResponse =>
                F.pure(Some(contextResponse.response.pipeBodyThrough(
                  _.onFinalizeCaseWeak(ec =>
                    release(contextRequest.context, Some(contextResponse.context), ec.toOutcome))))))
              .guaranteeCase {
                  case Outcome.Succeeded(_) =>
                    F.unit
                  case otherwise =>
                    release(contextRequest.context, None, otherwise.void)

              })
        ))
    // format: on

  /** Bracket on the start of a request and the completion of processing the
    * response ''body Stream''.
    *
    * @note $releaseWarning
    *
    * @note A careful reader might be wondering where the analogous `use`
    *       parameter from `cats.effect.Bracket` has gone. The use of the
    *       acquired resource is running the request, thus the `use` function
    *       is actually just the normal context route function from http4s
    *       `Kleisli[OptionT[F, *], ContextRequest[F, A], Response[F]]`.
    *
    * @param acquire Effect to run each time a request is received. The result
    *        of it is put into a `ContextRequest` and passed to the underlying
    *        routes.
    *
    * @param release Effect to run at the termination of each response body,
    *        or on any error after `acquire` has run. Will always be called
    *        exactly once if `acquire` is invoked, for each request/response.
    */
  def bracketRequestResponseCaseRoutes[F[_], A](
      acquire: F[A]
  )(
      release: (A, Outcome[F, Throwable, Unit]) => F[Unit]
  )(implicit F: MonadCancelThrow[F]): ContextMiddleware[F, A] =
    contextRoutes =>
      bracketRequestResponseCaseRoutes_[F, A, Unit](req =>
        acquire.map(a => ContextRequest(a, req))
      ) { case (a, _, oc) => release(a, oc) }(F)(
        contextRoutes.map(resp => ContextResponse[F, Unit]((), resp))
      )

  /** As [[bracketRequestResponseCaseRoutes]] but defined for [[HttpApp]],
    * rather than [[HttpRoutes]].
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseCaseApp[F[_], A](
      acquire: F[A]
  )(release: (A, Outcome[F, Throwable, Unit]) => F[Unit])(implicit
      F: MonadCancelThrow[F]
  ): Kleisli[F, ContextRequest[F, A], Response[F]] => Kleisli[F, Request[F], Response[F]] =
    (contextService: Kleisli[F, ContextRequest[F, A], Response[F]]) =>
      Kleisli((request: Request[F]) =>
        acquire.flatMap((a: A) =>
          contextService
            .run(ContextRequest(a, request))
            .map(_.pipeBodyThrough(_.onFinalizeCaseWeak(ec => release(a, ec.toOutcome))))
            .guaranteeCase {
              case Outcome.Succeeded(_) =>
                F.unit
              case otherwise =>
                release(a, otherwise.void)

            }
        )
      )

  /** As [[bracketRequestResponseCaseRoutes]], but `release` is simplified, ignoring
    * the exit condition.
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseRoutes[F[_], A](acquire: F[A])(release: A => F[Unit])(implicit
      F: MonadCancelThrow[F]
  ): ContextMiddleware[F, A] =
    bracketRequestResponseCaseRoutes[F, A](acquire) { case (a, _) =>
      release(a)
    }

  /** As [[bracketRequestResponseCaseApp]], but `release` is simplified, ignoring
    * the exit condition.
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseApp[F[_], A](acquire: F[A])(release: A => F[Unit])(implicit
      F: MonadCancelThrow[F]
  ): Kleisli[F, ContextRequest[F, A], Response[F]] => Kleisli[F, Request[F], Response[F]] =
    bracketRequestResponseCaseApp[F, A](acquire) { case (a, _) =>
      release(a)
    }

  /** As [[bracketRequestResponseRoutes]], but `acquire` and `release` are
    * defined in terms of a [[cats.effect.Resource]].
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseRoutesR[F[_], A](
      resource: Resource[F, A]
  )(implicit F: MonadCancelThrow[F]): ContextMiddleware[F, A] = {
    (contextRoutes: ContextRoutes[A, F]) =>
      val contextRoutes0: ContextRoutes[(A, F[Unit]), F] =
        contextRoutes.local(_.map(_._1))
      bracketRequestResponseRoutes(
        resource.allocated
      )(_._2)(F)(contextRoutes0)
  }

  /** As [[bracketRequestResponseApp]], but `acquire` and `release` are defined
    * in terms of a [[cats.effect.Resource]].
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseAppR[F[_], A](
      resource: Resource[F, A]
  )(implicit
      F: MonadCancelThrow[F]
  ): Kleisli[F, ContextRequest[F, A], Response[F]] => Kleisli[F, Request[F], Response[F]] = {
    (contextApp: Kleisli[F, ContextRequest[F, A], Response[F]]) =>
      val contextApp0: Kleisli[F, ContextRequest[F, (A, F[Unit])], Response[F]] =
        contextApp.local(_.map(_._1))
      bracketRequestResponseApp(
        resource.allocated
      )(_._2)(F)(contextApp0)
  }

  @deprecated("Use ExitCase.toOutcome instead", "0.23.8")
  def exitCaseToOutcome[F[_]](
      ec: ExitCase
  )(implicit F: Applicative[F]): Outcome[F, Throwable, Unit] =
    ec match {
      case ExitCase.Succeeded => Outcome.succeeded(F.unit)
      case ExitCase.Errored(e) => Outcome.errored(e)
      case ExitCase.Canceled => Outcome.canceled
    }
}
