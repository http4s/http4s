package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.{Enumeratee, Iteratee}
import scala.language.implicitConversions
import concurrent.ExecutionContext
import com.typesafe.config.{ConfigFactory, Config}
import org.joda.time.{DateTime, DateTimeZone, ReadableInstant}
import org.joda.time.format.DateTimeFormat
import java.util.Locale

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

  val Get = Method.Get
  val Post = Method.Post
  val Put = Method.Put
  val Delete = Method.Delete
  val Trace = Method.Trace
  val Options = Method.Options
  val Patch = Method.Patch
  val Head = Method.Head
  val Connect = Method.Connect

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

  private[this] val Rfc1123Format = DateTimeFormat
    .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
    .withLocale(Locale.US)
    .withZone(DateTimeZone.UTC);

  implicit class RichReadableInstant(instant: ReadableInstant) {
    def formatRfc1123: String = Rfc1123Format.print(instant)
  }

  val UnixEpoch = new DateTime(0)
}
