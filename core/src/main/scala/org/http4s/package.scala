package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.{Enumeratee, Iteratee}
import scala.language.implicitConversions
import concurrent.ExecutionContext
import com.typesafe.config.{ConfigFactory, Config}

package object http4s {
  type Route = PartialFunction[RequestPrelude, Iteratee[HttpChunk, Responder]]

  type ResponderBody = Enumeratee[HttpChunk, HttpChunk]

  type Middleware = (Route => Route)

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

//  implicit def request2scope(req: RequestPrelude) = RequestScope(req.uuid)
//  implicit def app2scope(routes: RouteHandler) = routes.appScope
//  implicit def attribute2defaultScope[T, S <: Scope](attributeKey: AttributeKey[T])(implicit scope: S) = attributeKey in scope
  implicit def string2headerkey(name: String): HttpHeaderKey[HttpHeader] = HttpHeaders.Key(name)

  /*
  type RequestRewriter = PartialFunction[Request, Request]

  def rewriteRequest(f: RequestRewriter): Middleware = {
    route: Route => f.orElse({ case req: Request => req }: RequestRewriter).andThen(route)
  }

  type ResponseTransformer = PartialFunction[Response, Response]

  def transformResponse(f: ResponseTransformer): Middleware = {
    route: Route => route andThen { handler => handler.map(f) }
  }
  */
}
