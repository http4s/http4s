package org.http4s
package server
package middleware
package authentication

import org.http4s.headers.Authorization

/**
 * Provides Basic Authentication from RFC 2617.
 * @param realm The realm used for authentication purposes.
 * @param store A partial function mapping (realm, user) to the
 *              appropriate password.
 */
class BasicAuthentication(realm: String, store: AuthenticationStore) extends Authentication {
  def getChallenge(req: Request): Option[Challenge] = checkAuth(req) match {
    case OK => None
    case _ => Some(Challenge("Basic", realm, Nil.toMap))
  }

  private def checkAuth(req: Request): AuthReply = {
    req.headers.foldLeft(NoAuthorizationHeader: AuthReply) {
      case (acc, h) =>
        if (acc != NoAuthorizationHeader)
          acc
        else h match {
          case Authorization(auth) =>
            auth.credentials match {
              case BasicCredentials(user, client_pass) =>
                if (!store.isDefinedAt((realm, user)))
                  UserUnknown
                else
                  store((realm, user)) match {
                    case server_pass if server_pass == client_pass => OK
                    case _ => WrongPassword
                  }
              case _ => NoCredentials
            }
          case _ => NoAuthorizationHeader
        }
    }
  }
}
