package org.http4s
package server
package middleware
package authentication

import java.util.{TimerTask, Timer}
import org.http4s.headers.Authorization
import scalaz.concurrent.Task
import scala.collection.immutable.Map

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
class DigestAuthentication(realm: String, store: AuthenticationStore, nonceCleanupInterval: Long = 3600000, nonceStaleTime: Long = 3600000, nonceBits: Int = 160) extends Authentication {
  private val nonceKeeper = new NonceKeeper(nonceStaleTime, nonceBits)
  private val timer = new Timer(true)
  scheduleStaleNonceRemoval()

  private def scheduleStaleNonceRemoval(): Unit = {
    val task: TimerTask = new TimerTask {
      override def run(): Unit = {
        nonceKeeper.removeStale()
        scheduleStaleNonceRemoval()
      }
    }
    timer.schedule(task, nonceCleanupInterval)
  }

  /**
   * Cancels the nonce cleanup thread.
   */
  def shutdown() = timer.cancel()

  /** Side-effect of running the returned task: If req contains a valid
    * AuthorizationHeader, the corresponding nonce counter (nc) is increased.
    */
  def getChallenge(req: Request) = {
    def paramsToChallenge(params: Map[String, String]) = Some(Challenge("Digest", realm, params))
    checkAuth(req).flatMap(_ match {
      case OK => Task.now(None)
      case StaleNonce => getChallengeParams(true).map(paramsToChallenge)
      case _ => getChallengeParams(false).map(paramsToChallenge)
    })
  }

  case object StaleNonce extends AuthReply

  case object BadNC extends AuthReply

  case object WrongResponse extends AuthReply

  case object BadParameters extends AuthReply

  private def checkAuth(req: Request) = Task {
    req.headers.get(Authorization) match {
      case None => NoAuthorizationHeader
      case Some(auth) => auth.credentials match {
        case GenericCredentials(AuthScheme.Digest, params) =>
          checkAuthParams(req, params)
        case _ => NoCredentials
      }
    }
  }

  private def getChallengeParams(staleNonce: Boolean) = Task {
    val nonce = nonceKeeper.newNonce()
    val m = Map("qop" -> "auth", "nonce" -> nonce)
    if (staleNonce)
      m + ("stale" -> "TRUE")
    else
      m
  }

  private def checkAuthParams(req: Request, params: Map[String, String]): AuthReply = {
    if (!(Set("realm", "nonce", "nc", "username", "cnonce", "qop") subsetOf params.keySet))
      return BadParameters

    val method = req.method.toString
    val uri = req.uri.toString

    if (params.get("realm") != Some(realm))
      return BadParameters

    val nonce = params("nonce")
    val nc = params("nc")
    nonceKeeper.receiveNonce(nonce, Integer.parseInt(nc, 16)) match {
      case NonceKeeper.StaleReply => StaleNonce
      case NonceKeeper.BadNCReply => BadNC
      case NonceKeeper.OKReply =>
        if (!store.isDefinedAt((realm, params("username"))))
          UserUnknown
        else
          store((realm, params("username"))) match {
            case password => {
              val resp = DigestUtil.computeResponse(method, params("username"), realm, password, uri, nonce, nc, params("cnonce"), params("qop"))
              if (resp == params("response"))
                OK
              else
                WrongResponse
            }
          }
    }
  }
}
