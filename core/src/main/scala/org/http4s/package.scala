package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.Enumerator
import scala.language.implicitConversions
import scala.concurrent.Future
//import spray.http.HttpHeaders.RawHeader

package object http4s {
  type Route = PartialFunction[Request[Chunk], Future[Responder[Chunk]]]

  /*
   * Alternatively...
   *
   * type Raw = (Iteratee[Chunk, Any] => Any)
   * val EmptyBody: Raw = { it: Iteratee[Chunk, Any] => it.feed(Input.EOF) }
   */
  //type Raw = Enumerator[Chunk]
  val EmptyBody: Enumerator[Chunk] = Enumerator.eof

  type Chunk = Array[Byte]

  type Middleware = (Route => Route)

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  trait RouteHandler {
    def route: Route
  }

//  /**
//   * Warms up the spray.http module by triggering the loading of most classes in this package,
//   * so as to increase the speed of the first usage.
//   */
//  def warmUp() {
//    HttpRequest(
//      headers = List(
//        RawHeader("Accept", "*/*,text/plain,custom/custom"),
//        RawHeader("Accept-Charset", "*,UTF-8"),
//        RawHeader("Accept-Encoding", "gzip,custom"),
//        RawHeader("Accept-Language", "*,nl-be,custom"),
//        RawHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
//        RawHeader("Cache-Control", "no-cache"),
//        RawHeader("Connection", "close"),
//        RawHeader("Content-Disposition", "form-data"),
//        RawHeader("Content-Encoding", "deflate"),
//        RawHeader("Content-Length", "42"),
//        RawHeader("Content-Type", "application/json"),
//        RawHeader("Cookie", "http4s=cool"),
//        RawHeader("Host", "http4s.org"),
//        RawHeader("X-Forwarded-For", "1.2.3.4"),
//        RawHeader("Fancy-Custom-Header", "yeah")
//      ),
//      entity = "http4s thanks spray greatly!"
//    ).parseAll
//    HttpResponse(status = 200)
//  }

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