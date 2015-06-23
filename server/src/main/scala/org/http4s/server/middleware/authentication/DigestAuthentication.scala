package org.http4s
package server
package middleware
package authentication

import java.security.SecureRandom
import java.math.BigInteger
import java.util.Date

import org.http4s.headers.Authorization

import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz._

/**
 * Provides Digest Authentication from RFC 2617. Note that this class creates a new thread
 * on creation to clean up stale nonces. Please call {@link shutdown()} when the object is not
 * used anymore to kill this thread.
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
class DigestAuthentication(realm: String, store: AuthenticationStore, nonceCleanupInterval: Duration = 3600.seconds, nonceStaleTime: Duration = 3600.seconds, nonceBits: Int = 160) extends Authentication {

  private trait AuthReply
  private case class OK(user: String, realm: String) extends AuthReply
  private case object StaleNonce extends AuthReply
  private case object BadNC extends AuthReply
  private case object WrongResponse extends AuthReply
  private case object BadParameters extends AuthReply
  private case object UserUnknown extends AuthReply
  private case object NoCredentials extends AuthReply
  private case object NoAuthorizationHeader extends AuthReply

  private val nonceKeeper = new NonceKeeper(nonceStaleTime.toMillis, nonceCleanupInterval.toMillis, nonceBits)

  /** Side-effect of running the returned task: If req contains a valid
    * AuthorizationHeader, the corresponding nonce counter (nc) is increased.
    */
  protected def getChallenge(req: Request): Task[Challenge\/Request] = {
    def paramsToChallenge(params: Map[String, String]) = -\/(Challenge("Digest", realm, params))
    checkAuth(req).flatMap(_ match {
      case OK(user, realm) => Task.now(\/-(addUserRealmAttributes(req, user, realm)))
      case StaleNonce => getChallengeParams(true).map(paramsToChallenge)
      case _          => getChallengeParams(false).map(paramsToChallenge)
    })
  }

  private def checkAuth(req: Request): Task[AuthReply] = req.headers.get(Authorization) match {
      case Some(Authorization(GenericCredentials(AuthScheme.Digest, params))) => checkAuthParams(req, params)
      case Some(Authorization(_)) => Task.now(NoCredentials)
      case None => Task.now(NoAuthorizationHeader)
  }

  private def getChallengeParams(staleNonce: Boolean): Task[Map[String, String]] = Task {
    val nonce = nonceKeeper.newNonce()
    val m = Map("qop" -> "auth", "nonce" -> nonce)
    if (staleNonce)
      m + ("stale" -> "TRUE")
    else
      m
  }

  private def checkAuthParams(req: Request, params: Map[String, String]): Task[AuthReply] = {
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
        store(realm, params("username")).map {
          case None => UserUnknown
          case Some(password) =>
            val resp = DigestUtil.computeResponse(method, params("username"), realm, password, uri, nonce, nc, params("cnonce"), params("qop"))

            if (resp == params("response")) OK(params("username"), realm)
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
