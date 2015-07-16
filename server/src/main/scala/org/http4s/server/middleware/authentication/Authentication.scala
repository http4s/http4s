package org.http4s
package server
package middleware
package authentication

import scalaz.concurrent.Task
import scalaz._
import org.http4s.headers.`WWW-Authenticate`

/**
 * Authentication instances are middleware that provide a
 * {@link HttpService} with HTTP authentication.
 */
trait Authentication extends HttpMiddleware {
  /**
   * Check if req contains valid credentials. You may assume that
   * the returned Task is executed at most once (to allow for side-effects, 
   * e.g. the incrementation of a nonce counter in DigestAuthentication).
   * @param req The request received from the client.
   * @return If req contains valid credentials, a copy of req is returned that
   *         contains additional attributes pertaining to authentication such
   *         as the username and realm from the valid credentials.
   *         If req does not contain valid credentials, a challenge is returned. 
   *         This challenge will be included in the HTTP 401 
   *         Unauthorized response that is returned to the client.
   *
   */
  protected def getChallenge(req: Request): Task[Challenge \/ Request]

  // Utility function for implementors of getChallenge()
  protected def addUserRealmAttributes(req: Request, user: String, realm: String) : Request =
    req.withAttribute(authenticatedUser,user).withAttribute(authenticatedRealm, realm)

  def apply(service: HttpService): HttpService = Service.lift { req =>
    getChallenge(req) flatMap {
      case \/-(req) =>
        service(req)
      case -\/(challenge) =>
        Task.now(Response(Status.Unauthorized).putHeaders(`WWW-Authenticate`(challenge)))
    }
  }
}

