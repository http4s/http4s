package org

import scala.concurrent.Future

package object http4s {
  type Route = PartialFunction[Request, Future[Responder]]

  type Chunk = Array[Byte]

  type Middleware = (Route => Route)

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