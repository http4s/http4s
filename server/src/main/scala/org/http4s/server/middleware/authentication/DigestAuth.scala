package org.http4s
package server
package middleware
package authentication

import cats.Applicative
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.Sync
import cats.implicits._
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Date
import org.http4s.headers._
import scala.concurrent.duration._

/**
  * Provides Digest Authentication from RFC 2617.
  */
object DigestAuth {
  /**
    * A function mapping username to a user object and password, or
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

  /**
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
  def apply[F[_]: Sync, A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceCleanupInterval: Duration = 1.hour,
      nonceStaleTime: Duration = 1.hour,
      nonceBits: Int = 160
  ): AuthMiddleware[F, A] = {
    val nonceKeeper =
      new NonceKeeper(nonceStaleTime.toMillis, nonceCleanupInterval.toMillis, nonceBits)
    challenged(challenge(realm, store, nonceKeeper))
  }

  /** Side-effect of running the returned task: If req contains a valid
    * AuthorizationHeader, the corresponding nonce counter (nc) is increased.
    */
  def challenge[F[_], A](realm: String, store: AuthenticationStore[F, A], nonceKeeper: NonceKeeper)(
      implicit F: Sync[F]): Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]] =
    Kleisli { req =>
      def paramsToChallenge(params: Map[String, String]) =
        Either.left(Challenge("Digest", realm, params))

      checkAuth(realm, store, nonceKeeper, req).flatMap {
        case OK(authInfo) => F.pure(Either.right(AuthedRequest(authInfo, req)))
        case StaleNonce => getChallengeParams(nonceKeeper, staleNonce = true).map(paramsToChallenge)
        case _ => getChallengeParams(nonceKeeper, staleNonce = false).map(paramsToChallenge)
      }
    }

  private def checkAuth[F[_], A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceKeeper: NonceKeeper,
      req: Request[F])(implicit F: Applicative[F]): F[AuthReply[A]] =
    req.headers.get(Authorization) match {
      case Some(Authorization(Credentials.AuthParams(AuthScheme.Digest, params))) =>
        checkAuthParams(realm, store, nonceKeeper, req, params)
      case Some(Authorization(_)) =>
        F.pure(NoCredentials)
      case None =>
        F.pure(NoAuthorizationHeader)
    }

  private def getChallengeParams[F[_]](nonceKeeper: NonceKeeper, staleNonce: Boolean)(
      implicit F: Sync[F]): F[Map[String, String]] =
    F.delay {
      val nonce = nonceKeeper.newNonce()
      val m = Map("qop" -> "auth", "nonce" -> nonce)
      if (staleNonce)
        m + ("stale" -> "TRUE")
      else
        m
    }

  private def checkAuthParams[F[_], A](
      realm: String,
      store: AuthenticationStore[F, A],
      nonceKeeper: NonceKeeper,
      req: Request[F],
      paramsNel: NonEmptyList[(String, String)])(implicit F: Applicative[F]): F[AuthReply[A]] = {
    val params = paramsNel.toList.toMap
    if (!Set("realm", "nonce", "nc", "username", "cnonce", "qop").subsetOf(params.keySet))
      return F.pure(BadParameters)

    val method = req.method.toString
    val uri = req.uri.toString

    if (params.get("realm") != Some(realm))
      return F.pure(BadParameters)

    val nonce = params("nonce")
    val nc = params("nc")
    nonceKeeper.receiveNonce(nonce, Integer.parseInt(nc, 16)) match {
      case NonceKeeper.StaleReply => F.pure(StaleNonce)
      case NonceKeeper.BadNCReply => F.pure(BadNC)
      case NonceKeeper.OKReply =>
        store(params("username")).map {
          case None => UserUnknown
          case Some((authInfo, password)) =>
            val resp = DigestUtil.computeResponse(
              method,
              params("username"),
              realm,
              password,
              uri,
              nonce,
              nc,
              params("cnonce"),
              params("qop"))

            if (resp == params("response")) OK(authInfo)
            else WrongResponse
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
