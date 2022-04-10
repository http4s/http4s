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

package org.http4s
package server
package middleware
package authentication

import cats.Monad
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all._
import org.http4s.crypto.Hash
import org.http4s.headers._

import scala.concurrent.duration._

/** Provides Digest Authentication from RFC 2617.
  */
object DigestAuth {

  @deprecated(
    "AuthenticationStore is going away, in favor of explicit subclasses of AuthStore. PlainTextAuthStore maintains the previous, insecure behaviour, whereas Md5HashedAuthStore is the new advised implementation going forward.",
    "0.23.12",
  )
  type AuthenticationStore[F[_], A] = String => F[Option[(A, String)]]

  sealed trait AuthStore[F[_], A] {
    private[DigestAuth] def func: String => F[Option[(A, String)]]
  }

  /** A function mapping username to a user object and password, or
    * None if no user exists.
    *
    * Requires that the server can recover the password in clear text,
    * which is _strongly_ discouraged. Please use Md5HashedAuthStore
    * if you can.
    */
  object PlainTextAuthStore {
    def apply[F[_], A](func: String => F[Option[(A, String)]]): AuthStore[F, A] =
      new PlainTextAuthStore(func)
  }
  final class PlainTextAuthStore[F[_], A](val func: String => F[Option[(A, String)]])
      extends AuthStore[F, A]

  /** A function mapping username to a user object and precomputed md5
    * hash of the username, realm, and password, or None if no user exists.
    *
    * More secure than PlainTextAuthStore due to only needing to
    * store the digested hash instead of the password in plain text.
    */
  object Md5HashedAuthStore {
    def apply[F[_], A](func: String => F[Option[(A, String)]]): AuthStore[F, A] =
      new Md5HashedAuthStore(func)

    def precomputeHash[F[_]: Monad: Hash](
        username: String,
        realm: String,
        password: String,
    ): F[String] =
      DigestUtil.computeHa1(username, realm, password)
  }
  final class Md5HashedAuthStore[F[_], A](val func: String => F[Option[(A, String)]])
      extends AuthStore[F, A]

  private trait AuthReply[+A]
  private final case class OK[A](authInfo: A) extends AuthReply[A]
  private case object StaleNonce extends AuthReply[Nothing]
  private case object BadNC extends AuthReply[Nothing]
  private case object WrongResponse extends AuthReply[Nothing]
  private case object BadParameters extends AuthReply[Nothing]
  private case object UserUnknown extends AuthReply[Nothing]
  private case object NoCredentials extends AuthReply[Nothing]
  private case object NoAuthorizationHeader extends AuthReply[Nothing]

  @deprecated("Calling apply is side-effecting, please use applyF", "0.23.12")
  def apply[F[_]: Sync, A](
      realm: String,
      store: String => F[Option[(A, String)]],
      nonceCleanupInterval: Duration = 1.hour,
      nonceStaleTime: Duration = 1.hour,
      nonceBits: Int = 160,
  ): AuthMiddleware[F, A] = {
    val nonceKeeper =
      new NonceKeeper(nonceStaleTime.toMillis, nonceCleanupInterval.toMillis, nonceBits)
    challenged(challenge(realm, store, nonceKeeper))
  }

  /** @param realm The realm used for authentication purposes.
    * @param store A partial function mapping (realm, user) to the
    *              appropriate password.
    * @param nonceCleanupInterval Interval (in milliseconds) at which stale
    *                             nonces should be cleaned up.
    * @param nonceStaleTime Amount of time (in milliseconds) after which a nonce
    *                       is considered stale (i.e. not used for authentication
    *                       purposes anymore).
    * @param nonceBits The number of random bits a nonce should consist of.
    */
  def applyF[F[_], A](
      realm: String,
      store: AuthStore[F, A],
      nonceCleanupInterval: Duration = 1.hour,
      nonceStaleTime: Duration = 1.hour,
      nonceBits: Int = 160,
  )(implicit F: Async[F]): F[AuthMiddleware[F, A]] =
    challenge[F, A](
      realm = realm,
      store = store,
      nonceCleanupInterval = nonceCleanupInterval,
      nonceStaleTime = nonceStaleTime,
      nonceBits = nonceBits,
    ).map { runChallenge =>
      challenged(runChallenge)
    }

  @deprecated(
    "Uses a side-effecting NonceKeeper. Use challenge(String, AuthStore, Blocker, Duration, Int, Int).",
    "0.23.12",
  )
  def challenge[F[_], A](
      realm: String,
      store: String => F[Option[(A, String)]],
      nonceKeeper: NonceKeeper,
  )(implicit
      F: Sync[F]
  ): Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]] =
    challengeInterop[F, A](
      realm,
      PlainTextAuthStore(store),
      F.delay(nonceKeeper.newNonce()),
      (data, nc) => F.delay(nonceKeeper.receiveNonce(data, nc)),
    )

  /** Similar to [[apply]], but exposing the underlying [[challenge]]
    * [[cats.data.Kleisli]] instead of an entire [[AuthMiddleware]]
    *
    * Side-effect of running the returned task: If req contains a valid
    * AuthorizationHeader, the corresponding nonce counter (nc) is increased.
    *
    * @param realm The realm used for authentication purposes.
    * @param store A partial function mapping (realm, user) to the
    *              appropriate password.
    * @param nonceCleanupInterval Interval (in milliseconds) at which stale
    *                             nonces should be cleaned up.
    * @param nonceStaleTime Amount of time (in milliseconds) after which a nonce
    *                       is considered stale (i.e. not used for authentication
    *                       purposes anymore).
    * @param nonceBits The number of random bits a nonce should consist of.
    */
  def challenge[F[_], A](
      realm: String,
      store: AuthStore[F, A],
      nonceCleanupInterval: Duration = 1.hour,
      nonceStaleTime: Duration = 1.hour,
      nonceBits: Int = 160,
  )(implicit
      F: Async[F]
  ): F[Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]]] =
    NonceKeeperF[F](nonceStaleTime, nonceCleanupInterval, nonceBits)
      .map { nonceKeeper =>
        challengeInterop[F, A](realm, store, nonceKeeper.newNonce(), nonceKeeper.receiveNonce _)
      }

  private[this] def challengeInterop[F[_], A](
      realm: String,
      store: AuthStore[F, A],
      newNonce: F[String],
      receiveNonce: (String, Int) => F[NonceKeeper.Reply],
  )(implicit
      F: Sync[F]
  ): Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]] =
    Kleisli { req =>
      def toChallenge(nonce: String, staleNonce: Boolean): Either[Challenge, Nothing] = {
        val m = Map("qop" -> "auth", "nonce" -> nonce)
        val params = if (staleNonce) m + ("stale" -> "TRUE") else m
        Either.left(Challenge("Digest", realm, params))
      }

      checkAuth(realm, store, receiveNonce, req).flatMap {
        case OK(authInfo) => F.pure(Right(AuthedRequest(authInfo, req)))
        case StaleNonce => newNonce.map(toChallenge(_, true))
        case _ => newNonce.map(toChallenge(_, false))
      }
    }

  private def checkAuth[F[_]: Hash, A](
      realm: String,
      store: AuthStore[F, A],
      receiveNonce: (String, Int) => F[NonceKeeper.Reply],
      req: Request[F],
  )(implicit F: Monad[F]): F[AuthReply[A]] =
    req.headers.get[Authorization] match {
      case Some(Authorization(Credentials.AuthParams(AuthScheme.Digest, params))) =>
        if (params.hasAllAuthParams && params.isInsideRealm(realm))
          checkAuthParams(realm, store, receiveNonce, req, params)
        else F.pure(BadParameters)
      case Some(_) => F.pure(NoCredentials)
      case None => F.pure(NoAuthorizationHeader)
    }

  implicit private class ParamsSyntax(private val params: NonEmptyList[(String, String)])
      extends AnyVal {
    def isInsideRealm(realm: String): Boolean =
      params.exists(p => p._1 == "realm" && p._2 == realm)

    def hasAllAuthParams: Boolean =
      allTheDigestAuthParams.forall(ap => params.exists(_._1 == ap))

    def valueOf(name: String): String =
      params.collectFirst { case (`name`, value) => value }.get
  }

  private[this] val allTheDigestAuthParams =
    List("realm", "nonce", "nc", "username", "cnonce", "qop")

  private def checkAuthParams[F[_]: Hash, A](
      realm: String,
      store: AuthStore[F, A],
      receiveNonce: (String, Int) => F[NonceKeeper.Reply],
      req: Request[F],
      params: NonEmptyList[(String, String)],
  )(implicit F: Monad[F]): F[AuthReply[A]] = {
    val nonce = params.valueOf("nonce")
    val nc = params.valueOf("nc")

    receiveNonce(nonce, Integer.parseInt(nc, 16)).flatMap {
      case NonceKeeper.StaleReply => F.pure(StaleNonce)
      case NonceKeeper.BadNCReply => F.pure(BadNC)
      case NonceKeeper.OKReply =>
        store.func(params.valueOf("username")).flatMap {
          case None => F.pure(UserUnknown)
          case Some((authInfo, password)) =>
            val method = req.method.toString
            val responseF: F[String] = store match {
              case _: PlainTextAuthStore[F, A] =>
                DigestUtil
                  .computeResponse(
                    method,
                    params.valueOf("username"),
                    realm,
                    password,
                    req.uri,
                    nonce,
                    nc,
                    params.valueOf("cnonce"),
                    params.valueOf("qop"),
                  )
              case _: Md5HashedAuthStore[F, A] =>
                DigestUtil
                  .computeHashedResponse(
                    method,
                    password,
                    req.uri,
                    nonce,
                    nc,
                    params.valueOf("cnonce"),
                    params.valueOf("qop"),
                  )
            }
            val expectedResponse = params.valueOf("response")
            responseF.map(r => if (r == expectedResponse) OK(authInfo) else WrongResponse)
        }
    }
  }
}
