package org.http4s
package grizzly

import org.glassfish.grizzly.http.server.{Response,Request=>GrizReq,HttpHandler}

import java.net.{URI, InetAddress}
import scala.collection.JavaConverters._
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Done
import org.http4s.Status.NotFound

/**
 * @author Bryce Anderson
 */

class Http4sGrizzly(route: Route, chunkSize: Int = 32 * 1024)(implicit executor: ExecutionContext = ExecutionContext.global) extends HttpHandler {

  override def service(req: GrizReq, resp: Response) {
    resp.suspend()  // Suspend the response until we close it

    val request = toRequest(req)
    val parser = route.lift(request).getOrElse(Done(NotFound(request)))
    val handler = parser.flatMap { responder =>
      resp.setStatus(responder.prelude.status.code, responder.prelude.status.reason)
      for (header <- responder.prelude.headers)
        resp.addHeader(header.name, header.value)
      val out = new OutputIteratee(resp.getNIOOutputStream, chunkSize)
      responder.body.transform(out)
    }
    new BodyEnumerator(req.getNIOInputStream, chunkSize)
      .run(handler)
      .onComplete(_ => resp.resume())
  }

  protected def toRequest(req: GrizReq): RequestPrelude =
    RequestPrelude(
      requestMethod = Method(req.getMethod.toString),
      uri = URI.create(req.getRequestURL.append("?").append(Option(req.getQueryString).getOrElse("")).toString),
      pathInfo = Option(req.getPathInfo).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol.getProtocolString),
      headers = toHeaders(req),
      serverSoftware = ServerSoftware(req.getServerName),
      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
    )

  protected def toHeaders(req: GrizReq): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield HttpHeaders.RawHeader(name, value)
    Headers(headers.toSeq : _*)
  }
}
