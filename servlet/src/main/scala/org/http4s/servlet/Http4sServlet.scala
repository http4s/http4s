package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import play.api.libs.iteratee.{Done, Iteratee, Enumerator}
import java.net.InetAddress
import scala.collection.JavaConverters._
import concurrent.{ExecutionContext,Future}
import javax.servlet.{ServletConfig, AsyncContext}
import org.http4s.Status.NotFound

class Http4sServlet(route: Route, chunkSize: Int = 32 * 1024)(implicit executor: ExecutionContext = ExecutionContext.global) extends HttpServlet {
  private[this] var serverSoftware: ServerSoftware = _

  override def init(config: ServletConfig) {
    serverSoftware = ServerSoftware(config.getServletContext.getServerInfo)
  }

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    val ctx = servletRequest.startAsync()
    executor.execute {
      new Runnable {
        def run() {
          handle(ctx)
        }
      }
    }
  }

  protected def handle(ctx: AsyncContext) {
    val servletRequest = ctx.getRequest.asInstanceOf[HttpServletRequest]
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    val request = toRequest(servletRequest)
    val parser = route.lift(request).getOrElse(Done(NotFound(request)))
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

  protected def toRequest(req: HttpServletRequest): RequestPrelude = {
    import AsyncContext._
    RequestPrelude(
      requestMethod = Method(req.getMethod),
      scriptName = stringAttribute(req, ASYNC_CONTEXT_PATH) + stringAttribute(req, ASYNC_SERVLET_PATH),
      pathInfo = Option(stringAttribute(req, ASYNC_PATH_INFO)).getOrElse(""),
      queryString = Option(stringAttribute(req, ASYNC_QUERY_STRING)).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol),
      headers = toHeaders(req),
      urlScheme = UrlScheme(req.getScheme),
      serverSoftware = serverSoftware,
      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
    )
  }

  private def stringAttribute(req: HttpServletRequest, key: String): String = req.getAttribute(key).asInstanceOf[String]

  protected def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield HttpHeaders.RawHeader(name, value)
    Headers(headers.toSeq : _*)
  }
}
