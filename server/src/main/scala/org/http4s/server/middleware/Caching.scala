/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats._
import cats.implicits._
import cats.effect._
import cats.data._
import org.http4s._
import org.http4s.util.CaseInsensitiveString
import org.http4s.headers.{Date => HDate, _}
import scala.concurrent.duration._

/** Caching contains middlewares to support caching functionality.
  *
  * Helper functions to support [[Caching.cache]] can be found in
  * [[Caching.Helpers]]
  */
object Caching {

  /** Middleware that implies responses should NOT be cached.
    * This is a best attempt, many implementors of caching have done so differently.
    */
  def `no-store`[G[_]: Monad: Clock, F[_], A](
      http: Kleisli[G, A, Response[F]]): Kleisli[G, A, Response[F]] =
    Kleisli { (a: A) =>
      for {
        resp <- http(a)
        out <- `no-store-response`[G](resp)
      } yield out
    }

  /** Transform a Response so that it will not be cached.
    */
  def `no-store-response`[G[_]]: PartiallyAppliedNoStoreCache[G] =
    new PartiallyAppliedNoStoreCache[G] {
      def apply[F[_]](resp: Response[F])(implicit M: Monad[G], C: Clock[G]): G[Response[F]] =
        HttpDate.current[G].map(now => resp.putHeaders(HDate(now) :: noStoreStaticHeaders: _*))
    }

  // These never change, so don't recreate them each time.
  private val noStoreStaticHeaders = List(
    `Cache-Control`(
      NonEmptyList.of[CacheDirective](
        CacheDirective.`no-store`,
        CacheDirective.`private`(),
        CacheDirective.`no-cache`(),
        CacheDirective.`max-age`(0.seconds)
      )
    ),
    Header("Pragma", "no-cache"),
    Expires(HttpDate.Epoch) // Expire at the epoch for no time confusion
  )

  /** Helpers Contains the default arguments used to help construct
    * middleware with [[caching]]. They serve to support the default arguments for
    * [[publicCache]] and [[privateCache]].
    */
  object Helpers {
    def defaultStatusToSetOn(s: Status): Boolean =
      s match {
        case Status.NotModified => true
        case otherwise => otherwise.isSuccess
      }

    def defaultMethodsToSetOn(m: Method): Boolean = methodsToSetOn.contains(m)

    private lazy val methodsToSetOn: Set[Method] = Set(
      Method.GET,
      Method.HEAD
    )
  }

  /** Sets headers for response to be publicly cached for the specified duration.
    *
    * Note: If set to Duration.Inf, lifetime falls back to
    * 10 years for support of Http1 caches.
    */
  def publicCache[G[_]: MonadError[*[_], Throwable]: Clock, F[_]](
      lifetime: Duration,
      http: Http[G, F]): Http[G, F] =
    cache(
      lifetime,
      Either.left(CacheDirective.public),
      Helpers.defaultMethodsToSetOn,
      Helpers.defaultStatusToSetOn,
      http)

  /** Publicly Cache a Response for the given lifetime.
    *
    * Note: If set to Duration.Inf, lifetime falls back to
    * 10 years for support of Http1 caches.
    */
  def publicCacheResponse[G[_]](lifetime: Duration): PartiallyAppliedCache[G] =
    cacheResponse(lifetime, Either.left(CacheDirective.public))

  /** Sets headers for response to be privately cached for the specified duration.
    *
    * Note: If set to Duration.Inf, lifetime falls back to
    * 10 years for support of Http1 caches.
    */
  def privateCache[G[_]: MonadError[*[_], Throwable]: Clock, F[_]](
      lifetime: Duration,
      http: Http[G, F],
      fieldNames: List[CaseInsensitiveString] = Nil): Http[G, F] =
    cache(
      lifetime,
      Either.right(CacheDirective.`private`(fieldNames)),
      Helpers.defaultMethodsToSetOn,
      Helpers.defaultStatusToSetOn,
      http)

  /** Privately Caches A Response for the given lifetime.
    *
    * Note: If set to Duration.Inf, lifetime falls back to
    * 10 years for support of Http1 caches.
    */
  def privateCacheResponse[G[_]](
      lifetime: Duration,
      fieldNames: List[CaseInsensitiveString] = Nil
  ): PartiallyAppliedCache[G] =
    cacheResponse(lifetime, Either.right(CacheDirective.`private`(fieldNames)))

  /** Construct a Middleware that will apply the appropriate caching headers.
    *
    * Helper functions for methodToSetOn and statusToSetOn can be found in [[Helpers]].
    *
    * Note: If set to Duration.Inf, lifetime falls back to
    * 10 years for support of Http1 caches.
    */
  def cache[G[_]: MonadError[*[_], Throwable]: Clock, F[_]](
      lifetime: Duration,
      isPublic: Either[CacheDirective.public.type, CacheDirective.`private`],
      methodToSetOn: Method => Boolean,
      statusToSetOn: Status => Boolean,
      http: Http[G, F]
  ): Http[G, F] =
    Kleisli { (req: Request[F]) =>
      for {
        resp <- http(req)
        out <-
          if (methodToSetOn(req.method) && statusToSetOn(resp.status))
            cacheResponse[G](lifetime, isPublic)(resp)
          else resp.pure[G]
      } yield out
    }

  // Here as an optimization so we don't recreate durations
  // in cacheResponse #TeamStatic
  private val tenYearDuration: FiniteDuration = 315360000.seconds

  /**  Method in order to turn a generated Response into one that
    * will be appropriately cached.
    *
    *  Note: If set to Duration.Inf, lifetime falls back to
    * 10 years for support of Http1 caches.
    */
  def cacheResponse[G[_]](
      lifetime: Duration,
      isPublic: Either[CacheDirective.public.type, CacheDirective.`private`]
  ): PartiallyAppliedCache[G] = {
    val actualLifetime = lifetime match {
      case finite: FiniteDuration => finite
      case _ => tenYearDuration
      // Http1 caches do not respect max-age headers, so to work globally it is recommended
      // to explicitly set an Expire which requires some time interval to work
    }
    new PartiallyAppliedCache[G] {
      override def apply[F[_]](
          resp: Response[F])(implicit M: MonadError[G, Throwable], C: Clock[G]): G[Response[F]] =
        for {
          now <- HttpDate.current[G]
          expires <-
            HttpDate
              .fromEpochSecond(now.epochSecond + actualLifetime.toSeconds)
              .liftTo[G]
        } yield {
          val headers = List(
            `Cache-Control`(
              NonEmptyList.of(
                isPublic.fold[CacheDirective](identity, identity),
                CacheDirective.`max-age`(actualLifetime)
              )),
            HDate(now),
            Expires(expires)
          )
          resp.putHeaders(headers: _*)
        }

    }
  }

  trait PartiallyAppliedCache[G[_]] {
    def apply[F[_]](
        resp: Response[F])(implicit M: MonadError[G, Throwable], C: Clock[G]): G[Response[F]]
  }

  trait PartiallyAppliedNoStoreCache[G[_]] {
    def apply[F[_]](resp: Response[F])(implicit M: Monad[G], C: Clock[G]): G[Response[F]]
  }
}
