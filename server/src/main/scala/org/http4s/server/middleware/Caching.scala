package org.http4s.server.middleware

import cats._
import cats.implicits._
import cats.effect._
import cats.data._
import org.http4s._
import org.http4s.util.CaseInsensitiveString
import org.http4s.headers.{Date => HDate, _}
import scala.concurrent.duration._

/**
  * Caching contains middlewares to support caching functionality.
  *
  * Helper functions to support [[Caching.cache]] can be found in
  * [[Caching.Helpers]]
  */
object Caching {

  /**
    * Middleware that implies responses should NOT be cached.
    * This is a best attempt, many implementors of caching have done so differently.
    */
  def `no-store`[G[_]: Monad: Clock, F[_], A](
      http: Kleisli[G, A, Response[F]]): Kleisli[G, A, Response[F]] =
    Kleisli { a: A =>
      for {
        resp <- http(a)
        now <- HttpDate.current[G]
      } yield {
        val headers = List(
          `Cache-Control`(
            NonEmptyList.of[CacheDirective](
              CacheDirective.`no-store`,
              CacheDirective.`private`(),
              CacheDirective.`no-cache`(),
              CacheDirective.`max-age`(0.seconds)
            )),
          Header("Pragma", "no-cache"),
          HDate(now),
          Expires(HttpDate.Epoch) // Expire at the epoch for no time confusion
        )
        resp.putHeaders(headers: _*)
      }
    }

  /**
    * Helpers Contains the default arguments used to help construct
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

  /**
    * Sets headers for response to be publicly cached for the specified duration.
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

  /**
    * Sets headers for response to be privately cached for the specified duration.
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

  /**
    * Construct a Middleware that will apply the appropriate caching headers.
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
  ): Http[G, F] = {
    val actualLifetime = lifetime match {
      case finite: FiniteDuration => finite
      case _ => 315360000.seconds // 10 years
      // Http1 caches do not respect max-age headers, so to work globally it is recommended
      // to explicitly set an Expire which requires some time interval to work
    }
    Kleisli { req: Request[F] =>
      for {
        resp <- http(req)
        out <- if (methodToSetOn(req.method) && statusToSetOn(resp.status)) {
          HttpDate.current[G].flatMap { now =>
            HttpDate
              .fromEpochSecond(now.epochSecond + actualLifetime.toSeconds)
              .liftTo[G]
              .map { expires =>
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
        } else resp.pure[G]
      } yield out
    }
  }

}
