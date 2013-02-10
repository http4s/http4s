package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.Enumerator
import scala.language.implicitConversions
import scala.concurrent.Future

package object http4s {
  type Route = PartialFunction[Request[Chunk], Future[Responder[Chunk]]]

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

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  trait RouteHandler {
    def route: Route
  }

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