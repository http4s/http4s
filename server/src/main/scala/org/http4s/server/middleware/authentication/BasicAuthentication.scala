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
  def apply(realm: String, validate: ValidatePassword): AuthMiddleware[(String, String)] = {
    challenged(Service.lift { req =>
      getChallenge(realm, validate, req, validatePassword _)
    })
  }

  /**
    * Construct authentication middleware that compares the client-provided password
    * to a plaintext password stored on the server.
    * @param realm
    * @param store
    * @return
    */
  def apply(realm: String, store: AuthenticationStore): AuthMiddleware[(String, String)] =
    challenged(Service.lift { req =>
      getChallenge(realm, store, req, comparePasswords _)
    })

  private trait AuthReply
  private sealed case class OK(user: String, realm: String) extends AuthReply
  private case object NeedsAuth extends AuthReply

  private def getChallenge[A](realm: String, store: A, req: Request, doCheck: (String, A, Request) => Task[AuthReply]) =
    doCheck(realm, store, req).map {
      case OK(user, realm) => \/-(AuthedRequest((user, realm), req))
      case NeedsAuth       => -\/(Challenge("Basic", realm, Nil.toMap))
    }

  private def validatePassword(realm: String, validate: ValidatePassword, req: Request): Task[AuthReply] = {
    req.headers.get(Authorization) match {
      case Some(Authorization(BasicCredentials(user, client_pass))) =>
        validate(realm, user, client_pass).map {
          case Some(true) => OK(user, realm)
          case _ => NeedsAuth
        }

      case Some(Authorization(_)) => Task.now(NeedsAuth)

      case None => Task.now(NeedsAuth)
    }
  }

  private def comparePasswords(realm: String, store: AuthenticationStore, req: Request): Task[AuthReply] = {
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
