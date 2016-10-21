package org.http4s
package server
package middleware

import scalaz._
import scalaz.concurrent.Task
import org.http4s.headers._

package object authentication {
  type Realm = String
  type User = String
  type ClientPasssword = String
  // A function mapping (realm, username) to password, None if no password
  // exists for that (realm, username) pair.
  type AuthenticationStore = (Realm, User) => Task[Option[String]]
  /**
    *  Validates a plaintext password (presumably by comparing it to a hashed value).
    *  Some(true) means the password given is valid.
    */
  type ValidatePassword = (Realm, User, ClientPasssword) => Task[Option[Boolean]]

  def challenged[A](challenge: Service[Request, Challenge \/ AuthedRequest[A]])
                   (service: AuthedService[A]): HttpService =
    Service.lift { req =>
      challenge(req) flatMap {
        case \/-(authedRequest) =>
          service(authedRequest)
        case -\/(challenge) =>
          Task.now(Response(Status.Unauthorized).putHeaders(`WWW-Authenticate`(challenge)))
      }
    }
}
