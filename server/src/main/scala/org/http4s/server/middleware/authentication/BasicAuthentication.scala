package org.http4s
package server
package middleware
package authentication

import org.http4s.headers.Authorization
import scalaz._
import scalaz.concurrent.Task

/**
 * Provides Basic Authentication from RFC 2617.
 * @param realm The realm used for authentication purposes.
 * @param store A partial function mapping (realm, user) to the
 *              appropriate password.
 */
class BasicAuthentication(realm: String, store: AuthenticationStore) extends Authentication {

  private trait AuthReply
  private sealed case class OK(user: String, realm: String) extends AuthReply
  private case object NeedsAuth extends AuthReply

  protected def getChallenge(req: Request) = checkAuth(req).map {
    case OK(user, realm) => \/-(addUserRealmAttributes(req, user, realm))
    case NeedsAuth       => -\/(Challenge("Basic", realm, Nil.toMap))
  }

  private def checkAuth(req: Request): Task[AuthReply] = {
    req.headers.get(Authorization) match {
      case Some(Authorization(BasicCredentials(user, client_pass))) =>
        store(realm, user).map {
          case None => NeedsAuth
          case Some(server_pass) =>
            if (server_pass == client_pass) OK(user, realm)
            else NeedsAuth
        }

      case Some(Authorization(_)) => Task.now(NeedsAuth)

      case None => Task.now(NeedsAuth)
    }
  }
}
