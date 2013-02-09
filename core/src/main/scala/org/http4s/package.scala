package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.Iteratee

package object http4s {
  type Route = PartialFunction[Request, Handler]

  type Handler = Iteratee[Chunk, Responder]

  type Chunk = Array[Byte]

  type Middleware = (Route => Route)

  object HeaderNames {
    val XForwardedFor = "X-Forwarded-For"
    val XForwardedProto = "X-Forwarded-Proto"
    val FrontEndHttps = "Front-End-Https"
    val Referer = "Referer"
    val AcceptLanguage = "Accept-Language"
  }

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

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