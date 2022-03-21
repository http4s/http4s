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
import cats.effect.Concurrent
import cats.effect.Sync
import cats.effect.Timer
import cats.syntax.all._
import org.http4s.crypto.Hash
import org.http4s.crypto.unsafe.SecureRandom
import org.http4s.headers._

import java.math.BigInteger
import java.util.Date
import scala.concurrent.duration._

/** Provides Digest Authentication from RFC 2617.
  */
object DigestAuth {

  /** A function mapping username to a user object and password, or
    * None if no user exists.  Requires that the server can recover
    * the password in clear text, which is _strongly_ discouraged.
    */
  type AuthenticationStore[F[_], A] = String => F[Option[(A, String)]]

  private trait AuthReply[+A]
  private final case class OK[A](authInfo: A) extends AuthReply[A]
  private case object StaleNonce extends AuthReply[Nothing]
  private case object BadNC extends AuthReply[Nothing]
  private case object WrongResponse extends AuthReply[Nothing]
  private case object BadParameters extends AuthReply[Nothing]
  private case object UserUnknown extends AuthReply[Nothing]
  private case object NoCredentials extends AuthReply[Nothing]
  private case object NoAuthorizationHeader extends AuthReply[Nothing]

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
  def apply[F[_]: Concurrent: Timer, A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceCleanupInterval: Duration = 1.hour,
      nonceStaleTime: Duration = 1.hour,
      nonceBits: Int = 160,
  ): F[AuthMiddleware[F, A]] =
    challenge[F, A](
      realm = realm,
      store = store,
      nonceCleanupInterval = nonceCleanupInterval,
      nonceStaleTime = nonceStaleTime,
      nonceBits = nonceBits,
    ).map { runChallenge =>
      challenged(runChallenge)
    }

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
  def challenge[F[_]: Timer, A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceCleanupInterval: Duration = 1.hour,
      nonceStaleTime: Duration = 1.hour,
      nonceBits: Int = 160,
  )(implicit F: Concurrent[F]): F[Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]]] =
    NonceKeeper[F](nonceStaleTime.toMillis, nonceCleanupInterval.toMillis, nonceBits)
      .map { nonceKeeper =>
        Kleisli { req =>
          def paramsToChallenge(params: Map[String, String]) =
            Either.left(Challenge("Digest", realm, params))

          checkAuth(realm, store, nonceKeeper, req).flatMap {
            case OK(authInfo) => F.pure(Either.right(AuthedRequest(authInfo, req)))
            case StaleNonce =>
              getChallengeParams(nonceKeeper, staleNonce = true).map(paramsToChallenge)
            case _ => getChallengeParams(nonceKeeper, staleNonce = false).map(paramsToChallenge)
          }
        }
      }

  private def checkAuth[F[_]: Hash, A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceKeeper: NonceKeeper[F],
      req: Request[F],
  )(implicit F: Monad[F]): F[AuthReply[A]] =
    req.headers.get[Authorization] match {
      case Some(Authorization(Credentials.AuthParams(AuthScheme.Digest, params))) =>
        checkAuthParams(realm, store, nonceKeeper, req, params)
      case Some(_) =>
        F.pure(NoCredentials)
      case None =>
        F.pure(NoAuthorizationHeader)
    }

  private def getChallengeParams[F[_]](nonceKeeper: NonceKeeper[F], staleNonce: Boolean)(implicit
      F: Sync[F]
  ): F[Map[String, String]] =
    nonceKeeper
      .newNonce()
      .map { nonce =>
        val m = Map("qop" -> "auth", "nonce" -> nonce)
        if (staleNonce)
          m + ("stale" -> "TRUE")
        else
          m
      }

  private def checkAuthParams[F[_]: Hash, A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceKeeper: NonceKeeper[F],
      req: Request[F],
      paramsNel: NonEmptyList[(String, String)],
  )(implicit F: Monad[F]): F[AuthReply[A]] = {
    val params = paramsNel.toList.toMap
    if (!Set("realm", "nonce", "nc", "username", "cnonce", "qop").subsetOf(params.keySet)) {
      F.pure(BadParameters)
    } else {
      val method = req.method.toString
      val uri = req.uri.toString

      if (!params.get("realm").contains(realm)) {
        F.pure(BadParameters)
      } else {
        val nonce = params("nonce")
        val nc = params("nc")
        nonceKeeper.receiveNonce(nonce, Integer.parseInt(nc, 16)).flatMap {
          case NonceKeeper.StaleReply => F.pure(StaleNonce)
          case NonceKeeper.BadNCReply => F.pure(BadNC)
          case NonceKeeper.OKReply =>
            store(params("username")).flatMap {
              case None => F.pure(UserUnknown)
              case Some((authInfo, password)) =>
                DigestUtil
                  .computeResponse(
                    method,
                    params("username"),
                    realm,
                    password,
                    uri,
                    nonce,
                    nc,
                    params("cnonce"),
                    params("qop"),
                  )
                  .map { resp =>
                    if (resp == params("response")) OK(authInfo)
                    else WrongResponse
                  }
            }
        }
      }
    }
  }
}

private[authentication] class Nonce(val created: Date, var nc: Int, val data: String)

private[authentication] object Nonce {
  val random = new SecureRandom()

  private def getRandomData(bits: Int): String = new BigInteger(bits, random).toString(16)

  def gen(bits: Int): Nonce = new Nonce(new Date(), 0, getRandomData(bits))
}
