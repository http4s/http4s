package org.http4s
package server

import scalaoauth2.provider._
import scala.concurrent.ExecutionContext
import scalaz.{\/, \/-, -\/}
import scalaz.concurrent.Task

import io.circe._

import org.http4s.Http4s._
import org.http4s.Status._
import org.http4s.headers._
import org.http4s.circe._
import org.http4s.util.task._

package object oauth2 {
  private[this] val logger = org.log4s.getLogger

  def issueAccessToken[A](req: Request, handler: AuthorizationHandler[A], tokenEndpoint: TokenEndpoint = TokenEndpoint)
                         (implicit ec: ExecutionContext): Task[Response] = {
    requestToOAuth2Request(req) flatMap { oauth2Req =>
      futureToTask(tokenEndpoint.handleRequest(oauth2Req, handler)) flatMap {
        case Right(r) =>
          Response(Ok).withBody(responseAccessToken(r))
            .putHeaders(`Cache-Control`(CacheDirective.`no-store`), Header.Raw("Pragma".ci, "no-cache"))
        case Left(e) =>
          Status.fromInt(e.statusCode) match {
            case \/-(status) =>
              Response(status)
                .withBody(responseOAuthErrorJson(e))
                .putHeaders(responseOAuthErrorHeader(e))
            case -\/(e) =>
              Task.fail(e)
         }
      }
    }
  }

  def authorize[A, U](req: Request, handler: ProtectedResourceHandler[U],
                      protectedResource: ProtectedResource = ProtectedResource)
                     (f: AuthInfo[U] => Task[Response])(implicit ec: ExecutionContext): Task[Response] = {
    val prReq = requestToProtectedResourceRequest(req)
    futureToTask(protectedResource.handleRequest(prReq, handler)) flatMap {
      case Right(authInfo) =>
        f(authInfo)
      case Left(e) =>
        Status.fromInt(e.statusCode) match {
          case \/-(status) =>
            Response(status)
              .withBody(responseOAuthErrorJson(e))
              .putHeaders(responseOAuthErrorHeader(e))
            case -\/(e) =>
              Task.fail(e)
         }
    }
  }

  private def responseAccessToken[U](r: GrantHandlerResult[U]) =
    Json.obj((Vector(
      Some("token_type" -> Json.string(r.tokenType)),
      Some("access_token" -> Json.string(r.accessToken)),
      r.expiresIn.map("expires_in" -> Json.long(_)),
      r.refreshToken.map("refresh_token" -> Json.string(_)),
      r.scope.map("scope" -> Json.string(_))
    ).collect { case Some(f) => f }):_*)

  private def responseOAuthErrorJson(e: OAuthError): Json =
    Json.obj(
      "error" -> Json.string(e.errorType),
      "error_description" -> Json.string(e.description)
    )

  private def responseOAuthErrorHeader(e: OAuthError): Header = {
    var params = Map("error" -> e.errorType)
    if (e.description.nonEmpty) params += "error_description" -> e.description
    `WWW-Authenticate`(Challenge("Bearer", params))
  }

  private def requestToOAuth2Request[A](req: Request): Task[AuthorizationRequest] = {
    req.attemptAs[UrlForm].fold(_ => Map.empty[String, Seq[String]], _.values).map { params =>
      new AuthorizationRequest(req.headers.toMap, params)
    }
  }

  private def requestToProtectedResourceRequest[A](req: Request): ProtectedResourceRequest = {
    val queryMap = req.uri.query.toVector.groupBy(_._1).mapValues(_.map(_._2.getOrElse("")))
    new ProtectedResourceRequest(req.headers.toMap, queryMap)
  }
}
