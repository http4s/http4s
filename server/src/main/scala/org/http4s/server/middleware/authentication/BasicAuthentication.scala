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
object basicAuth {
  /**
    * Construct authentication middleware that can validate the client-provided
    * plaintext password against something else (like a stored, hashed password).
    * @param realm
    * @param validate
    * @return
    */
  def apply(realm: String, validate: AuthenticationValidator): AuthMiddleware[(String, String)] = {
    challenged(Service.lift { req =>
      getChallenge(realm, validate, req)
    })
  }

  private trait AuthReply
  private sealed case class OK(user: String, realm: String) extends AuthReply
  private case object NeedsAuth extends AuthReply

  private def getChallenge[A](realm: String, validate: AuthenticationValidator, req: Request) =
    validatePassword(realm, validate, req).map {
      case OK(user, realm) => \/-(AuthedRequest((user, realm), req))
      case NeedsAuth       => -\/(Challenge("Basic", realm, Nil.toMap))
    }

  private def validatePassword(realm: String, validate: AuthenticationValidator, req: Request): Task[AuthReply] = {
    req.headers.get(Authorization) match {
      case Some(Authorization(BasicCredentials(user, client_pass))) =>
        validate(realm, user, client_pass).map({
          case Some(_) => OK(user, realm)
          case _ => NeedsAuth
        })

      case _ => Task.now(NeedsAuth)
    }
  }
}
