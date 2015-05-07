package org.http4s
package server
package middleware
package authentication

import scalaz.concurrent.Task
import org.http4s.headers.`WWW-Authenticate`

/**
 * Authentication instances are middleware that provide a
 * {@link HttpService} with HTTP authentication.
 */
trait Authentication {
  /**
   * Check if req contains valid credentials. You may assume that
   * the returned Task is executed at most once (to allow for side-effects, 
   * e.g. the incrementation of a nonce counter in DigestAuthentication).
   * @param req The request received from the client.
   * @return If req contains valid credentials, None is returned.
   *         Otherwise, Some(challenge) is returned. In this case,
   *         challenge will be included in the HTTP 401 Unauthorized
   *         response that is returned to the client.
   *
   */
  def getChallenge(req: Request): Task[Option[Challenge]]

  def apply(service: HttpService): HttpService = Service.lift {
    case req: Request => getChallenge(req).flatMap(_ match {
      case None => service(req)
      case Some(challenge) =>
        Task.now(Some(Response(Status.Unauthorized).putHeaders(`WWW-Authenticate`(challenge))))
    })
  }
}

abstract class AuthReply

