package org.http4s
package server
package middleware
package authentication

import java.security.SecureRandom
import java.math.BigInteger
import java.util.Date
import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.util.{Timeout => ATimeout}
import org.http4s.headers.Authorization
import scala.collection.immutable.HashMap

/**
 * Provides Digest Authentication from RFC 2617.
 * @param realm The realm used for authentication purposes.
 * @param store A partial function mapping (realm, user) to the
 *              appropriate password.
 * @param nonceCleanupInterval Interval (in seconds) at which stale
 *                             nonces should be cleaned up.
 * @param nonceStaleTime Amount of time (in seconds) after which a nonce
 *                       is considered stale (i.e. not used for authentication
 *                       purposes anymore).
 * @param nonceBits The number of random bits a nonce should consist of.
 */
class DigestAuthentication(realm: String, store: AuthenticationStore, nonceCleanupInterval: Long = 3600, nonceStaleTime: Long = 3600, nonceBits: Int = 160) extends Authentication {

  val actor_system = ActorSystem("DigestAuthentication")
  val nonce_keeper = actor_system.actorOf(Props(new NonceKeeper(nonceStaleTime * 1000, nonceBits)), "nonce_keeper")

  val cleanup_interval = nonceCleanupInterval.seconds

  import actor_system.dispatcher

  val remove_stale_task = actor_system.scheduler.schedule(cleanup_interval, cleanup_interval, nonce_keeper, NonceKeeper.RemoveStale)

  val stale_timeout = nonceStaleTime * 1000

  // Side-effect: If req contains a valid AuthorizationHeader, the
  // corresponding nonce counter (nc) is increased.
  def getChallenge(req: Request) = checkAuth(req) match {
    case OK => None
    case StaleNonce => Some(Challenge("Digest", realm, getChallengeParams(true)))
    case _ => Some(Challenge("Digest", realm, getChallengeParams(false)))
  }

  case object StaleNonce extends AuthReply

  case object BadNC extends AuthReply

  case object WrongResponse extends AuthReply

  case object BadParameters extends AuthReply

  private def checkAuth(req: Request): AuthReply = {
    req.headers.foldLeft(NoAuthorizationHeader: AuthReply) {
      case (acc, h) =>
        if (acc != NoAuthorizationHeader)
          acc
        else h match {
          case Authorization(auth) =>
            auth.credentials match {
              case GenericCredentials(AuthScheme.Digest, params) =>
                checkAuthParams(req, params)
              case _ => NoCredentials
            }
          case _ => NoAuthorizationHeader
        }
    }
  }

  private def getChallengeParams(staleNonce: Boolean) = {
    val f = ask(nonce_keeper, NonceKeeper.NewNonce)(ATimeout(5.seconds)).mapTo[String]
    val nonce = Await.result(f, 5.seconds)
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
    val f = ask(nonce_keeper, NonceKeeper.ReceiveNonce(nonce, Integer.parseInt(nc, 16)))(ATimeout(5.seconds)).mapTo[NonceKeeper.Reply]
    Await.result(f, 5.seconds) match {
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

class Nonce(val created: Date, var nc: Int, val data: String) {
}

object Nonce {
  val random = new SecureRandom()

  private def getRandomData(bits: Int) = new BigInteger(bits, random).toString(16)

  def apply(bits: Int) = {
    new Nonce(new Date(), 0, getRandomData(bits))
  }
}

object NonceKeeper {

  case object NewNonce

  case class ReceiveNonce(data: String, nc: Int)

  case object RemoveStale

  abstract class Reply

  case object StaleReply extends Reply

  case object OKReply extends Reply

  case object BadNCReply extends Reply

}

/**
 * An {@link Actor} used to manage a database of nonces.
 * Sending the {@link NonceKeeper.NewNonce} message yields a reply
 * containing a fresh nonce as a {@link String}. On receiving a
 * {@link NonceKeeper.ReceiveNonce} message, this actor checks if it
 * is known and the nc value is correct. If so, the nc value associated
 * with the nonce is increased and either {@link NonceKeeper.OKReply}
 * or {@link NonceKeeper.BadNCReply} is replied. Finally, on
 * receiving a {@link NonceKeeper.RemoveStale} message, the actor
 * removes nonces that are older than staleTimeout.
 */
class NonceKeeper(staleTimeout: Long, bits: Int) extends Actor {
  assert(bits > 0)
  var nonces: Map[String, Nonce] = new HashMap[String, Nonce]

  def isStale(past: Date, now: Date) = staleTimeout < now.getTime() - past.getTime()

  def receive = {
    case NonceKeeper.NewNonce => {
      var n: Nonce = null
      do {
        n = Nonce(bits)
      } while (nonces.contains(n.data))
      nonces = nonces + (n.data -> n)
      sender() ! n.data
    }
    case NonceKeeper.ReceiveNonce(data, nc) => {
      nonces.get(data) match {
        case None => sender() ! NonceKeeper.StaleReply
        case Some(n) => {
          if (nc > n.nc) {
            n.nc = n.nc + 1
            sender() ! NonceKeeper.OKReply
          } else
            sender() ! NonceKeeper.BadNCReply
        }
      }
    }
    case NonceKeeper.RemoveStale =>
      nonces = nonces.filter { case (_, n) =>
        !isStale(n.created, new Date())
      }
  }
}
