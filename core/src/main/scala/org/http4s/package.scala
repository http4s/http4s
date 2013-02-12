package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.Enumerator
import scala.language.implicitConversions
import scala.concurrent.Future
//import spray.http.HttpHeaders.RawHeader

package object http4s {
  type Route = PartialFunction[Request[Raw], Future[Responder[HttpChunk]]]

  type Raw = Array[Byte]
  type URI = String

  // Our Http message "currency" types
  sealed trait HasHeaders {
    def headers: Headers
  }

  sealed trait HttpPrelude extends HasHeaders

  sealed trait HttpChunk {
    def bytes: Raw
  }

  sealed trait HttpBodyChunk extends HttpChunk
  case class HttpEntity(bytes: Raw) extends HttpBodyChunk

  sealed trait MultipartEntity extends HttpBodyChunk {
    def name: String
    def contentType: String
  }
  case class MultipartChunk(bytes: Raw, contentType: String, name: String) extends MultipartEntity
  case class FileChunk(bytes: Raw, contentType: String, name: String) extends MultipartEntity

  case class RequestPrelude(method: Method, headers: Headers, uri: URI) extends HttpPrelude
  case class ResponsePrelude(status: StatusLine, headers: Headers) extends HttpPrelude
  case class HttpTrailer(headers: Headers) extends HasHeaders with HttpChunk {
    final val bytes = Array.empty[Byte]
  }

  // End currency


  val EmptyBody: Enumerator[HttpChunk] = Enumerator.eof
  val EmptyRequestBody: Enumerator[Raw] = Enumerator.eof

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