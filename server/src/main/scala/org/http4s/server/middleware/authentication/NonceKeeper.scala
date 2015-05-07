package org.http4s
package server
package middleware
package authentication

import scala.collection.mutable.{Map, HashMap}
import java.util.Date
import java.security.SecureRandom
import java.math.BigInteger

/**
 * A thread-safe class used to manage a database of nonces.
 *
 * @param staleTimeout Amount of time (in milliseconds) after which a nonce
 *                     is considered stale (i.e. not used for authentication
 *                     purposes anymore).
 * @param bits The number of random bits a nonce should consist of.
 */
class NonceKeeper(staleTimeout: Long, bits: Int) {
  require(bits > 0, "Please supply a positive integer for bits.")

  private val nonces: Map[String, Nonce] = new HashMap[String, Nonce]

  private def isStale(past: Date, now: Date) = staleTimeout < now.getTime() - past.getTime()

  /**
   * Removes nonces that are older than staleTimeout
   */
  def removeStale() = {
    val d = new Date()
    nonces.synchronized {
      nonces.retain { case (_, n) => !isStale(n.created, d) }
    }
  }

  /**
   * Get a fresh nonce in form of a {@link String}.
   * @return A fresh nonce.
   */
  def newNonce() = {
    var n: Nonce = null
    nonces.synchronized {
      do {
        n = Nonce(bits)
      } while (nonces.contains(n.data))
      nonces += (n.data -> n)
    }
    n.data
  }

  /**
   * Checks if the nonce {@link data} is known and the {@link nc} value is
   * correct. If this is so, the nc value associated with the nonce is increased
   * and the appropriate status is returned.
   * @param data The nonce.
   * @param nc The nonce counter.
   * @return A reply indicating the status of (data, nc).
   */
  def receiveNonce(data: String, nc: Int) =
    nonces.synchronized {
      nonces.get(data) match {
        case None => NonceKeeper.StaleReply
        case Some(n) => {
          if (nc > n.nc) {
            n.nc = n.nc + 1
            NonceKeeper.OKReply
          } else
            NonceKeeper.BadNCReply
        }
      }
    }
}

object NonceKeeper {

  sealed abstract class Reply

  case object StaleReply extends Reply

  case object OKReply extends Reply

  case object BadNCReply extends Reply

}

class Nonce(val created: Date, var nc: Int, val data: String) {
}

object Nonce {
  private val random = new SecureRandom()

  private def getRandomData(bits: Int) = new BigInteger(bits, random).toString(16)

  def apply(bits: Int) = {
    new Nonce(new Date(), 0, getRandomData(bits))
  }
}


