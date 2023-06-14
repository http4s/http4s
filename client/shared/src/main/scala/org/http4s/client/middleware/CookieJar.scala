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
            .map(r => r.domain.fold(r.copy(domain = req.uri.host.map(_.value)))(_ => r))
            .traverse_(alg.addCookie(_, req.uri))
        )
      } yield out
    }

  /** Constructor which builds a non-exposed CookieJar
    * and applies it to the client.
    */
  def impl[F[_]: Sync](c: Client[F]): F[Client[F]] =
    in[F, F](c)

  /** Like [[impl]] except it allows the creation of the middleware in a
    * different HKT than the client is in.
    */
  def in[F[_]: Sync, G[_]: Sync](c: Client[F]): G[Client[F]] =
    jarIn[F, G].map(apply(_)(c))

  /** Jar Constructor
    */
  def jarImpl[F[_]: Sync]: F[CookieJar[F]] =
    jarIn[F, F]

  /** Like [[jarImpl]] except it allows the creation of the CookieJar in a
    * different HKT than the client is in.
    */
  def jarIn[F[_]: Sync, G[_]: Sync]: G[CookieJar[F]] =
    Ref.in[G, F, Map[CookieKey, CookieValue]](Map.empty).map { ref =>
      new CookieJarRefImpl[F](ref) {}
    }

  private[CookieJar] class CookieJarRefImpl[F[_]: Sync](
      ref: Ref[F, Map[CookieKey, CookieValue]]
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
        out <- ref.update(extractFromResponseCookies(_)(cookies, now))
      } yield out

    override def cookies: F[List[ResponseCookie]] = ref.get.map(_.values.map(_.cookie).toList)

    override def enrichRequest[N[_]](r: Request[N]): F[Request[N]] =
      for {
        cookies <- ref.get.map(_.map(_._2.cookie).toList)
      } yield cookiesForRequest(r, cookies)
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
  ) {
    override def equals(obj: Any): Boolean =
      obj match {
        case c: CookieValue =>
          setAt == c.setAt &&
          expiresAt == c.expiresAt &&
          cookie == c.cookie
        case _ => false
      }
  }

  private[middleware] object CookieValue {
    def apply(
        setAt: HttpDate,
        expiresAt: HttpDate,
        cookie: ResponseCookie,
    ): CookieValue = new CookieValue(setAt, expiresAt, cookie)
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
      m: Map[CookieKey, CookieValue]
  )(
      cookies: G[(ResponseCookie, Uri)],
      httpDate: HttpDate,
  ): Map[CookieKey, CookieValue] =
    cookies
      .foldRight(Eval.now(m)) { case ((rc, uri), eM) =>
        eM.map(m => extractFromResponseCookie(m)(rc, httpDate, uri))
      }
      .value

  private[middleware] def extractFromResponseCookie(
      m: Map[CookieKey, CookieValue]
  )(c: ResponseCookie, httpDate: HttpDate, uri: Uri): Map[CookieKey, CookieValue] =
    c.domain.orElse(uri.host.map(_.value)) match {
      case Some(domainS) =>
        val key = CookieKey(c.name, domainS, c.path)
        val newCookie = c.copy(domain = domainS.some)
        val expires: HttpDate = expiresAt(httpDate, c, HttpDate.MaxValue)
        val value = CookieValue(httpDate, expires, newCookie)
        m + (key -> value)
      case None => // Ignore Cookies We Can't get a domain for
        m
    }

  private[middleware] def responseCookieToRequestCookie(r: ResponseCookie): RequestCookie =
    RequestCookie(r.name, r.content)

  private[middleware] def cookieAppliesToRequest[N[_]](
      r: Request[N],
      c: ResponseCookie,
  ): Boolean = {
    val domainApplies = c.domain.exists(s =>
      r.uri.host.forall { authority =>
        authority.renderString.contains(s)
      }
    )
    val pathApplies = c.path.forall(s => r.uri.path.renderString.contains(s))

    val secureSatisfied =
      if (c.secure)
        r.uri.scheme.exists { scheme =>
          scheme === Uri.Scheme.https
        }
      else true

    domainApplies && pathApplies && secureSatisfied
  }

  private[middleware] def cookiesForRequest[N[_]](
      r: Request[N],
      l: List[ResponseCookie],
  ): List[RequestCookie] =
    l.foldLeft(List.empty[RequestCookie]) { case (list, cookie) =>
      if (cookieAppliesToRequest(r, cookie)) responseCookieToRequestCookie(cookie) :: list
      else list
    }
}
