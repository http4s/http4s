package org

import play.api.libs.iteratee.Iteratee

package object http4s {
  type Route = PartialFunction[Request, Handler]

  type Handler = Iteratee[Array[Byte], Response]

  type Middleware = (Route => Route)

  type RequestRewriter = PartialFunction[Request, Request]

  type ResponseTransformer = PartialFunction[Response, Response]
}