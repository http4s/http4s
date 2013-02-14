package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import play.api.libs.iteratee.{Done, Iteratee, Enumerator}
import java.net.InetAddress
import scala.collection.JavaConverters._
import concurrent.{ExecutionContext,Future}
import javax.servlet.AsyncContext

class Http4sServlet(route: Route, chunkSize: Int = 32 * 1024)(implicit executor: ExecutionContext = ExecutionContext.global) extends HttpServlet {
  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    println(s"SERVLET PATH = ${servletRequest.getServletPath}")
    println(s"PATH INFO = ${servletRequest.getPathInfo}")

    val ctx = servletRequest.startAsync()
    executor.execute {
      new Runnable {
        def run() {
          handle(ctx, servletRequest, servletResponse)
        }
      }
    }
  }

  def handle(ctx: AsyncContext, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    val request = toRequest(servletRequest)
    val parser = route.lift(request).getOrElse(Done(ResponderGenerators.genRouteNotFound(request)))
    val handler = parser.flatMap { responder =>
      servletResponse.setStatus(responder.prelude.status.code, responder.prelude.status.reason)
      for (header <- responder.prelude.headers)
        servletResponse.addHeader(header.name, header.value)
      responder.body.transform(Iteratee.foreach { chunk =>
        val out = servletResponse.getOutputStream
        out.write(chunk.bytes)
        out.flush()
      })
    }
    Enumerator.fromStream(servletRequest.getInputStream, chunkSize)
      .map[HttpChunk](HttpEntity(_))
      .run(handler)
      .onComplete(_ => ctx.complete())
  }

  protected def toRequest(req: HttpServletRequest): RequestPrelude =
    RequestPrelude(
      requestMethod = Method(req.getMethod),
      scriptName = req.getContextPath + req.getServletPath,
      pathInfo = Option(req.getPathInfo).getOrElse(""),
      queryString = Option(req.getQueryString).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol),
      headers = toHeaders(req),
      urlScheme = UrlScheme(req.getScheme),
      serverName = req.getServerName,
      serverPort = req.getServerPort,
      serverSoftware = ServerSoftware(getServletContext.getServerInfo),
      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
    )

  protected def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield HttpHeaders.RawHeader(name, value)
    Headers(headers.toSeq : _*)
  }
}
