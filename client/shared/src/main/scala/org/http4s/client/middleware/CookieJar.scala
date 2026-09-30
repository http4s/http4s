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

package org.http4s.client.middleware

import cats._
import cats.effect.kernel._
import cats.syntax.all._
import org.http4s._
import org.http4s.client.Client
import org.typelevel.ci.CIString

import java.util.Locale

/** Algebra for Interfacing with the Cookie Jar.
  * Allows manual intervention and eviction.
  */
trait CookieJar[F[_]] {

  /** Default Expiration Approach, Removes Expired Cookies
    */
  def evictExpired: F[Unit]

  /** Available for Use To Relieve Memory Pressure
    */
  def evictAll: F[Unit]

  /** Add Cookie to the cookie jar
    */
  def addCookie(c: ResponseCookie, uri: Uri): F[Unit] =
    addCookies(List((c, uri)))

  /** Like addCookie but puts several in at once
    */
  def addCookies[G[_]: Foldable](cookies: G[(ResponseCookie, Uri)]): F[Unit]

  /** Extract the cookies from the middleware
    */
  def cookies: F[List[ResponseCookie]]

  /** Enrich a Request with the cookies available
    */
  def enrichRequest[G[_]](r: Request[G]): F[Request[G]]
}

/** Cookie Jar Companion Object
  * Contains constructors for client middleware or raw
  * jar creation, as well as the middleware
  */
object CookieJar {
  // RFC6265 6.1 says "at least 3000 cookies total", and I like powers
  // of two.
  private[middleware] val DefaultMaxCookies = 4096

  // It also says at least 50 cookies per domain.  A low cap prevents
  // one malicious response from overwhelming us.  Not implemented is
  // fairness, so multiple mischievous responses don't monopolize the
  // cookie jar.  If the dog bites us once, shame on http4s; if the
  // same same dog bites you twice, shame on you.
  private[middleware] val DefaultMaxCookiesPerResponse = 64

  /** Middleware Constructor Using a Provided [[CookieJar]].
    */
  def apply[F[_]: Sync](
      alg: CookieJar[F]
  )(
      client: Client[F]
  ): Client[F] =
    Client { req =>
      for {
        _ <- Resource.eval(alg.evictExpired)
        modRequest <- Resource.eval(alg.enrichRequest(req))
        out <- client.run(modRequest)
        _ <- Resource.eval(
          out.cookies
            .take(DefaultMaxCookiesPerResponse)
            .traverse_(alg.addCookie(_, req.uri))
        )
      } yield out
    }

  /** Constructor which builds a non-exposed CookieJar
    * and applies it to the client.
    */
  @deprecated("Call overload with a PublicSuffixMatcher.", "0.23.38")
  def impl[F[_]: Sync](c: Client[F]): F[Client[F]] =
    in[F, F](c)

  /** Constructor which builds a non-exposed CookieJar
    * and applies it to the client.
    */
  @deprecated("Call overload with a PublicSuffixMatcher.", "0.23.38")
  def in[F[_]: Sync, G[_]: Sync](c: Client[F]): G[Client[F]] =
    jarIn[F, G].map(apply(_)(c))

  def impl[F[_]: Sync](psl: PublicSuffixMatcher)(c: Client[F]): F[Client[F]] =
    in[F, F](psl)(c)


  /** Like `impl` except it allows the creation of the middleware in a
    * different HKT than the client is in.
    */
  def in[F[_]: Sync, G[_]: Sync](psl: PublicSuffixMatcher)(c: Client[F]): G[Client[F]] =
    jarIn[F, G](psl).map(apply(_)(c))

  @deprecated("Call overload with a PublicSuffixMatcher.", "0.23.38")
  def jarImpl[F[_]: Sync]: F[CookieJar[F]] =
    jarIn[F, F]

  /** Jar Constructor
    */
  def jarImpl[F[_]: Sync](psl: PublicSuffixMatcher): F[CookieJar[F]] =
    jarIn[F, F](psl)

  @deprecated("Call overload with a PublicSuffixMatcher.", "0.23.38")
  def jarIn[F[_]: Sync, G[_]: Sync]: G[CookieJar[F]] =
    Ref.in[G, F, Map[CookieKey, CookieValue]](Map.empty).map { ref =>
      new CookieJarRefImpl[F](ref, PublicSuffixMatcher.default, DefaultMaxCookies) {}
    }

  /** Like `jarImpl` except it allows the creation of the CookieJar in a
    * different HKT than the client is in.
    */
  def jarIn[F[_]: Sync, G[_]: Sync](psl: PublicSuffixMatcher): G[CookieJar[F]] =
    Ref.in[G, F, Map[CookieKey, CookieValue]](Map.empty).map { ref =>
      new CookieJarRefImpl[F](ref, psl, DefaultMaxCookies) {}
    }

  private[CookieJar] class CookieJarRefImpl[F[_]: Sync](
      ref: Ref[F, Map[CookieKey, CookieValue]],
      psl: PublicSuffixMatcher,
      maxCookies: Int,
  ) extends CookieJar[F] {
    override def evictExpired: F[Unit] =
      for {
        now <- HttpDate.current[F]
        out <- ref.update(
          _.filter { t =>
            now <= t._2.expiresAt
          }
        )
      } yield out

    override def evictAll: F[Unit] = ref.set(Map.empty)

    override def addCookies[G[_]: Foldable](cookies: G[(ResponseCookie, Uri)]): F[Unit] =
      for {
        now <- HttpDate.current[F]
        out <- ref.update(m => trim(extractFromResponseCookies(m, psl)(cookies, now)))
      } yield out

    override def cookies: F[List[ResponseCookie]] = ref.get.map(_.values.map(_.cookie).toList)

    private def trim(
        m: Map[CookieKey, CookieValue]
    ): Map[CookieKey, CookieValue] =
      if (m.size <= maxCookies) m
      else {
        // A priority queue would probably be faster, but it's a lot
        // of machinery for what's not a bothersome number.
        //
        // If lots of cookies share the same epoch second, the tiebreaker
        // is non-deterministic.
        m.toList
          .sortBy(_._2.setAt.epochSecond)(Ordering[Long].reverse)
          .take(maxCookies)
          .toMap
      }

    override def enrichRequest[N[_]](r: Request[N]): F[Request[N]] =
      for {
        values <- ref.get.map(_.values.toList)
      } yield cookiesForRequest(r, values, psl)
        .foldLeft(r) { case (req, cookie) => req.addCookie(cookie) }
  }

  private[middleware] final case class CookieKey(
      name: String,
      domain: String,
      path: Option[String],
  )

  private[middleware] final class CookieValue(
      val setAt: HttpDate,
      val expiresAt: HttpDate,
      val cookie: ResponseCookie,
      val hostOnly: Boolean,
  ) {
    override def equals(obj: Any): Boolean =
      obj match {
        case c: CookieValue =>
          setAt == c.setAt &&
          expiresAt == c.expiresAt &&
          cookie == c.cookie &&
          hostOnly == c.hostOnly
        case _ => false
      }
  }

  private[middleware] object CookieValue {
    def apply(
        setAt: HttpDate,
        expiresAt: HttpDate,
        cookie: ResponseCookie,
        hostOnly: Boolean,
    ): CookieValue = new CookieValue(setAt, expiresAt, cookie, hostOnly)
  }

  private[middleware] def expiresAt(
      now: HttpDate,
      c: ResponseCookie,
      default: HttpDate,
  ): HttpDate =
    c.expires
      .orElse(
        c.maxAge.flatMap(seconds => HttpDate.fromEpochSecond(now.epochSecond + seconds).toOption)
      )
      .getOrElse(default)

  private[middleware] def extractFromResponseCookies[G[_]: Foldable](
      m: Map[CookieKey, CookieValue],
      psl: PublicSuffixMatcher,
  )(
      cookies: G[(ResponseCookie, Uri)],
      httpDate: HttpDate,
  ): Map[CookieKey, CookieValue] =
    cookies
      .foldRight(Eval.now(m)) { case ((rc, uri), eM) =>
        eM.map(m => extractFromResponseCookie(m)(rc, httpDate, uri, psl))
      }
      .value

  private[middleware] def extractFromResponseCookie(
      m: Map[CookieKey, CookieValue]
  )(
      c: ResponseCookie,
      httpDate: HttpDate,
      uri: Uri,
      psl: PublicSuffixMatcher,
  ): Map[CookieKey, CookieValue] = {
    val (storedDomain, hostOnly) = c.domain match {
      case Some(d) =>
        if (uri.host.exists(domainMatches(_, d, psl))) (Some(canonicalDomain(d)), false)
        else (None, false)
      case None =>
        (uri.host.map(h => canonicalDomain(h.value)), true)
    }
    storedDomain match {
      case Some(domainS) =>
        val key = CookieKey(c.name, domainS, c.path)
        val newCookie = c.copy(domain = domainS.some)
        val expires: HttpDate = expiresAt(httpDate, c, HttpDate.MaxValue)
        val value = CookieValue(httpDate, expires, newCookie, hostOnly)
        m + (key -> value)
      case None => // Ignore Cookies We Can't get a domain for
        m
    }
  }

  private[middleware] def responseCookieToRequestCookie(r: ResponseCookie): RequestCookie =
    RequestCookie(r.name, r.content)

  private def domainMatches(
      host: Uri.Host,
      cookieDomain: String,
      psl: PublicSuffixMatcher,
  ): Boolean = {
    val requestHost = canonicalDomain(host.value)
    val domain = canonicalDomain(cookieDomain)
    domain.nonEmpty && {
      host match {
        case _: Uri.Ipv4Address | _: Uri.Ipv6Address =>
          requestHost == domain
        case _: Uri.RegName =>
          requestHost == domain ||
          (requestHost.endsWith("." + domain) && !psl.isPublicSuffix(CIString(domain)))
      }
    }
  }

  private def canonicalDomain(s: String): String =
    s.toLowerCase(Locale.ROOT).stripPrefix(".").stripSuffix(".")

  private def pathMatches(requestPath: Uri.Path, cookiePath: String): Boolean = {
    val requestPathStr = if (requestPath.isEmpty) "/" else requestPath.renderString
    (requestPathStr == cookiePath) ||
    (requestPathStr.startsWith(cookiePath) &&
      (cookiePath.endsWith("/") || requestPathStr.charAt(cookiePath.length) == '/'))
  }

  private[middleware] def cookieAppliesToRequest[N[_]](
      r: Request[N],
      c: ResponseCookie,
      psl: PublicSuffixMatcher,
      hostOnly: Boolean,
  ): Boolean = {
    def domainApplies =
      c.domain.exists(s =>
        r.uri.host.exists { host =>
          if (hostOnly) canonicalDomain(host.value) == canonicalDomain(s)
          else domainMatches(host, s, psl)
        }
      )
    def pathApplies = c.path.forall(s => pathMatches(r.uri.path, s))
    def secureSatisfied =
      if (c.secure)
        r.uri.scheme.exists { scheme =>
          scheme === Uri.Scheme.https
        }
      else true
    domainApplies && pathApplies && secureSatisfied
  }

  private[middleware] def cookiesForRequest[N[_]](
      r: Request[N],
      l: List[CookieValue],
      psl: PublicSuffixMatcher,
  ): List[RequestCookie] =
    l.foldLeft(List.empty[RequestCookie]) { case (list, value) =>
      if (cookieAppliesToRequest(r, value.cookie, psl, value.hostOnly))
        responseCookieToRequestCookie(value.cookie) :: list
      else list
    }
}

/** Pluggable public-suffix check used to prevent cookies from being scoped to
  * a public suffix (e.g. `com`).
  *
  * @param domain the domain, with leading dot stripped
  * @return `true` if `domain` is a public suffix.
  * @see https://datatracker.ietf.org/doc/html/rfc6265#section-5.3
  */
trait PublicSuffixMatcher {
  def isPublicSuffix(domain: CIString): Boolean
}

object PublicSuffixMatcher {

  /** Treats any single-label domain (no internal dot) as a public suffix.
    * Multi-label suffixes (e.g. `co.uk`) require a full PSL matcher.
    */
  val default: PublicSuffixMatcher = new PublicSuffixMatcher {
    def isPublicSuffix(domain: CIString): Boolean =
      domain.nonEmpty && !domain.toString.contains('.')
  }

  /** Disables public-suffix checking, restoring the behaviour that
    * GHSA-wv64-j4fq-5f9x and GHSA-jc8x-g44q-5x7j describe: a server can scope a
    * cookie to a top level domain and have it replayed to unrelated hosts under
    * it. Only reach for this if you validate cookie domains elsewhere.
    */
  val none: PublicSuffixMatcher = new PublicSuffixMatcher {
    def isPublicSuffix(domain: CIString): Boolean = false
  }
}
