package org

import scala.concurrent.Future
import play.api.libs.iteratee.{Input, Iteratee, Enumerator}

package object http4s {
  type Route = PartialFunction[Request[Raw], Future[Responder[Raw]]]

  /*
   * Alternatively...
   *
   * type Raw = (Iteratee[Chunk, Any] => Any)
   * val EmptyBody: Raw = { it: Iteratee[Chunk, Any] => it.feed(Input.EOF) }
   */
  type Raw = Enumerator[Chunk]
  val EmptyBody: Raw = Enumerator.eof

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