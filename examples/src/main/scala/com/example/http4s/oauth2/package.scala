package com.example.http4s

import org.http4s._
import org.http4s.dsl._
import org.http4s.server.oauth2._

import scalaoauth2.provider._

package object oauth2 {
  val handler = new MyDataHandler

  // TODO: get rid of this
  import scala.concurrent.ExecutionContext.Implicits.global

  val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.PASSWORD -> new Password {
        override def clientCredentialRequired = false
      }
    )
  }

  val oauth2Example = HttpService {
    case req @ POST -> Root / "access-token" =>
      issueAccessToken(req, handler, tokenEndpoint)

    case req @ GET -> Root / "secure-hello" =>
      authorize(req, handler) { authInfo => Ok(s"Hello, ${authInfo.user.name}.") }
  }
}
