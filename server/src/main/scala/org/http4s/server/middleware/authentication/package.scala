package org.http4s
package server
package middleware

import scalaz._
import scalaz.concurrent.Task
import org.http4s.headers._

package object authentication {
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
