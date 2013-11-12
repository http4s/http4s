package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.net.InetAddress
import scala.collection.JavaConverters._
import javax.servlet.{ServletConfig, AsyncContext}

import Http4sServlet._
import com.typesafe.scalalogging.slf4j.Logging
import scalaz.concurrent.Task
import scalaz.stream.io._
import scalaz.{-\/, \/-}
import scala.util.control.NonFatal

class Http4sServlet(service: HttpService, chunkSize: Int = DefaultChunkSize) extends HttpServlet with Logging {
  private[this] var serverSoftware: ServerSoftware = _

  override def init(config: ServletConfig) {
    serverSoftware = ServerSoftware(config.getServletContext.getServerInfo)
  }

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    try {
      val request = toRequest(servletRequest)
      val ctx = servletRequest.startAsync()
      handle(request, ctx)
    } catch {
      case NonFatal(e) => handleError(e, servletResponse)
    }
  }

  private def handleError(t: Throwable, response: HttpServletResponse) {
    logger.error("Error processing request", t)
    if (!response.isCommitted)
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
  }

  private def handle(request: Request, ctx: AsyncContext): Unit = {
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    Task.fork {
      service(request).flatMap { response =>
        servletResponse.setStatus(response.prelude.status.code, response.prelude.status.reason)
        for (header <- response.prelude.headers)
          servletResponse.addHeader(header.name, header.value)
        val out = servletResponse.getOutputStream
        val isChunked = response.prelude.headers.get(Header.`Transfer-Encoding`)
          .fold(false)(_.coding.matches(ContentCoding.chunked))
        response.body.map { bytes =>
          out.write(bytes.toArray)
          if (isChunked)
            servletResponse.flushBuffer()
        }.run
      }
    }.runAsync {
      case \/-(_) =>
        ctx.complete()
      case -\/(t) =>
        handleError(t, servletResponse)
        ctx.complete()
    }
  }

  protected def toRequest(req: HttpServletRequest): Request = {
    val prelude = RequestPrelude(
      requestMethod = Method(req.getMethod),
      scriptName = req.getContextPath + req.getServletPath,
      pathInfo = Option(req.getPathInfo).getOrElse(""),
      queryString = Option(req.getQueryString).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol),
      headers = toHeaders(req),
      urlScheme = HttpUrlScheme(req.getScheme),
      serverName = req.getServerName,
      serverPort = req.getServerPort,
      serverSoftware = serverSoftware,
      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
    )
    val body = chunkR(req.getInputStream).map(f => f(chunkSize).map(BodyChunk.apply _)).eval
    Request(prelude, body)
  }

  protected def toHeaders(req: HttpServletRequest): HeaderCollection = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header.RawHeader(name, value)
    HeaderCollection(headers.toSeq : _*)
  }
}

object Http4sServlet {
  private[servlet] val DefaultChunkSize = Http4sConfig.getInt("org.http4s.servlet.default-chunk-size")
}