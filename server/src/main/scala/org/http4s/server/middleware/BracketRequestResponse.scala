/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.data._
import cats.effect._
import cats.effect.syntax.all._
import cats.implicits._
import org.http4s._
import org.http4s.server._

/** Middelwares which allow for bracketing on a Request/Response, including
  * the completion of the Response body stream.
  *
  * These are analogous to `cats.effect.Bracket` and `fs2.Stream.bracket`. The
  * reason that they exist is because due to the full termination of a
  * Response being a function of the termination of the `fs2.Stream` which
  * backs the response body, you can't actually use either
  * `cats.effect.Bracket` or `fs2.Stream.bracket` directly.
  *
  * http4s has encodings similar to these in the main repo, but they are all
  * special cased. Having a generic implementation opens up the possibility of
  * writing many interesting middlewares with ease.
  *
  * @define releaseWarning The bracketing semantics defined here differ
  *         in one very important way from cats-effect or fs2 bracketing
  *         semantics. If the Response body is not evaluated after the
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

  /** A [[Response]] value with some arbitrary context added. */
  final case class ContextResponse[F[_], A](context: A, response: Response[F])

  /** A Middleware which uses both a [[ContextRequest]] and [[ContextResponse]]. */
  type FullContextMiddleware[F[_], A, B] =
    Kleisli[OptionT[F, *], ContextRequest[F, A], ContextResponse[F, B]] => Kleisli[
      OptionT[F, *],
      Request[F],
      Response[F]]

  /** Bracket on the start of a request and the completion of processing the
    * response ''body Stream''.
    *
    * This middleware uses a context on both the Request and Response. This is
    * the most general possible encoding for bracketing on a Request/Response
    * interaction. It is required to express certain types of bracketing use
    * cases (see Metrics), but for most cases it is more general than is
    * needed. Before attempting to use this middleware, you should consider if
    * [[#bracketRequestResponseRoutes]] or
    * [[#bracketRequestResponseCaseRoutes]] will work for your use case.
    *
    * @note $releaseWarning
    *
    * @note For some use cases, you might want to differentiate if release is
    *       invoked at the end of http routes expression, e.g. when the
    *       [[Response]] type is created, or if it is invoked at the
    *       termination of the response body stream. You can determine this by
    *       inspecting the `ExitCase` and the `Option[B]` response context. If
    *       the `ExitCase` is `Completed` then, you can be certain that
    *       release is invoked at the termination of the body stream. If the
    *       `ExitCase` is either `Error` or `Canceled`, then you can determine
    *       where `release` is invoked by inspecting the response context. If
    *       it is defined, e.g. `Some(_: B)`, then `release` is invoked at the
    *       termination of the body stream, otherwise it is invoked at the
    *       termination of the attempt to generated the [[Response]] value
    *       (which failed due to either error or cancellation). This is
    *       because the response context is only (and always) present if
    *       [[Response]] value was generated, but is not (and never will be)
    *       present if the generation of the [[Response]] value failed.
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
  )(release: (A, Option[B], ExitCase[Throwable]) => F[Unit])(implicit
      F: Bracket[F, Throwable]): FullContextMiddleware[F, A, B] =
    (bracketRoutes: Kleisli[OptionT[F, *], ContextRequest[F, A], ContextResponse[F, B]]) =>
      Kleisli((request: Request[F]) =>
        OptionT(
          acquire(request).flatMap(contextRequest =>
            bracketRoutes(contextRequest)
              .foldF(release(contextRequest.context, None, ExitCase.Completed) *> F.pure(
                None: Option[Response[F]]))(contextResponse =>
                F.pure(Some(contextResponse.response.copy(body =
                  contextResponse.response.body.onFinalizeCaseWeak(ec =>
                    release(contextRequest.context, Some(contextResponse.context), ec))))))
              .guaranteeCase {
                case ExitCase.Completed =>
                  F.unit
                case otherwise =>
                  release(contextRequest.context, None, otherwise)
              })
        ))

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
  )(release: (A, ExitCase[Throwable]) => F[Unit])(implicit
      F: Bracket[F, Throwable]): ContextMiddleware[F, A] =
    contextRoutes =>
      bracketRequestResponseCaseRoutes_[F, A, Unit](req =>
        acquire.map(a => ContextRequest(a, req))) { case (a, _, ec) => release(a, ec) }(F)(
        contextRoutes.map(resp => ContextResponse[F, Unit]((), resp)))

  /** As [[#bracketRequestResponseCaseRoutes]] but defined for [[HttpApp]],
    * rather than [[HttpRoutes]].
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseCaseApp[F[_], A](
      acquire: F[A]
  )(release: (A, ExitCase[Throwable]) => F[Unit])(implicit F: Bracket[F, Throwable])
      : Kleisli[F, ContextRequest[F, A], Response[F]] => Kleisli[F, Request[F], Response[F]] =
    (contextService: Kleisli[F, ContextRequest[F, A], Response[F]]) =>
      Kleisli((request: Request[F]) =>
        acquire.flatMap((a: A) =>
          contextService
            .run(ContextRequest(a, request))
            .map(response =>
              response.copy(body = response.body.onFinalizeCaseWeak(ec => release(a, ec))))
            .guaranteeCase {
              case ExitCase.Completed =>
                F.unit
              case otherwise =>
                release(a, otherwise)
            }))

  /** As [[#bracketRequestResponseCaseRoutes]], but `release` is simplified, ignoring
    * the exit condition.
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseRoutes[F[_], A](acquire: F[A])(release: A => F[Unit])(implicit
      F: Bracket[F, Throwable]): ContextMiddleware[F, A] =
    bracketRequestResponseCaseRoutes[F, A](acquire) { case (a, _) =>
      release(a)
    }

  /** As [[#bracketRequestResponseCaseApp]], but `release` is simplified, ignoring
    * the exit condition.
    *
    * @note $releaseWarning
    */
  def bracketRequestResponseApp[F[_], A](acquire: F[A])(release: A => F[Unit])(implicit
      F: Bracket[F, Throwable])
      : Kleisli[F, ContextRequest[F, A], Response[F]] => Kleisli[F, Request[F], Response[F]] =
    bracketRequestResponseCaseApp[F, A](acquire) { case (a, _) =>
      release(a)
    }
}
