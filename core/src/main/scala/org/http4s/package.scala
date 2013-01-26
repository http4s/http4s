package org

import play.api.libs.iteratee.Iteratee

package object http4s {
  type Route = PartialFunction[Request, Handler]

  type Handler = Iteratee[Array[Byte], Response]

  type Middleware = (Route => Route)

  type RequestRewriter = PartialFunction[Request, Request]

  def rewriteRequest(f: RequestRewriter): Middleware = {
    route: Route => f.orElse({ case req: Request => req }: RequestRewriter).andThen(route)
  }

  type ResponseTransformer = PartialFunction[Response, Response]

  def transformResponse(f: ResponseTransformer): Middleware = {
    route: Route => route andThen { handler => handler.map(f) }
  }
}