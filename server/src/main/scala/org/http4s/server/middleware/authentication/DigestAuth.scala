package org.http4s
package server
package middleware
package authentication

import java.security.SecureRandom
import java.math.BigInteger
import java.util.Date

import scala.concurrent.duration._

import cats.data._
import cats.implicits._
import fs2._
import org.http4s.headers._

/**
 * Provides Digest Authentication from RFC 2617.
 */
object DigestAuth {
  /**
   * A function mapping username to a user object and password, or
   * None if no user exists.  Requires that the server can recover
   * the password in clear text, which is _strongly_ discouraged.
   */
  type AuthenticationStore[A] = String => Task[Option[(A, String)]]

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
  def apply[A](
    realm: String,
    store: AuthenticationStore[A],
    nonceCleanupInterval: Duration = 1.hour,
    nonceStaleTime: Duration = 1.hour,
    nonceBits: Int = 160
  ): AuthMiddleware[A] = {
    val nonceKeeper = new NonceKeeper(nonceStaleTime.toMillis, nonceCleanupInterval.toMillis, nonceBits)
    challenged(challenge(realm, store, nonceKeeper))
  }

  /** Side-effect of running the returned task: If req contains a valid
    * AuthorizationHeader, the corresponding nonce counter (nc) is increased.
    */
  def challenge[A](realm: String, store: AuthenticationStore[A], nonceKeeper: NonceKeeper): Service[Request, Either[Challenge, AuthedRequest[A]]] =
    Service.lift { req => {
      def paramsToChallenge(params: Map[String, String]) = Either.left(Challenge("Digest", realm, params))

      checkAuth(realm, store, nonceKeeper, req).flatMap(_ match {
        case OK(authInfo) => Task.now(Either.right(AuthedRequest(authInfo, req)))
        case StaleNonce => getChallengeParams(nonceKeeper, true).map(paramsToChallenge)
        case _ => getChallengeParams(nonceKeeper, false).map(paramsToChallenge)
      })
    }
  }

  private def checkAuth[A](realm: String, store: AuthenticationStore[A], nonceKeeper: NonceKeeper, req: Request): Task[AuthReply[A]] = req.headers.get(Authorization) match {
    case Some(Authorization(Credentials.AuthParams(AuthScheme.Digest, params))) =>
      checkAuthParams(realm, store, nonceKeeper, req, params)
    case Some(Authorization(_)) =>
      Task.now(NoCredentials)
    case None =>
      Task.now(NoAuthorizationHeader)
  }

  private def getChallengeParams(nonceKeeper: NonceKeeper, staleNonce: Boolean): Task[Map[String, String]] = Task.delay {
    val nonce = nonceKeeper.newNonce()
    val m = Map("qop" -> "auth", "nonce" -> nonce)
    if (staleNonce)
      m + ("stale" -> "TRUE")
    else
      m
  }

  private def checkAuthParams[A](realm: String, store: AuthenticationStore[A], nonceKeeper: NonceKeeper, req: Request, paramsNel: NonEmptyList[(String, String)]): Task[AuthReply[A]] = {
    val params = paramsNel.toList.toMap
    if (!(Set("realm", "nonce", "nc", "username", "cnonce", "qop") subsetOf params.keySet))
      return Task.now(BadParameters)

    val method = req.method.toString
    val uri = req.uri.toString

    if (params.get("realm") != Some(realm))
      return Task.now(BadParameters)

    val nonce = params("nonce")
    val nc = params("nc")
    nonceKeeper.receiveNonce(nonce, Integer.parseInt(nc, 16)) match {
      case NonceKeeper.StaleReply => Task.now(StaleNonce)
      case NonceKeeper.BadNCReply => Task.now(BadNC)
      case NonceKeeper.OKReply =>
        store(params("username")).map {
          case None => UserUnknown
          case Some((authInfo, password)) =>
            val resp = DigestUtil.computeResponse(method, params("username"), realm, password, uri, nonce, nc, params("cnonce"), params("qop"))

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

  def gen(bits: Int): Nonce = {
    new Nonce(new Date(), 0, getRandomData(bits))
  }
}
