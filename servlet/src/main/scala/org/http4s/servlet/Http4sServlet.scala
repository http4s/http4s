package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.net.InetAddress
import scala.collection.JavaConverters._
import concurrent.{ExecutionContext,Future}
import javax.servlet.{ServletConfig, AsyncContext}
import org.http4s.Status.{InternalServerError, NotFound}

import Http4sServlet._
import scala.util.logging.Logged
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.ExecutorService
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process
import scala.util.control.NonFatal
import scalaz.stream.io._
import scalaz.stream.processes
import scalaz.{Monad, Catchable}
import scalaz.stream.Process.Process0

class Http4sServlet[F[_]](service: HttpService[F], chunkSize: Int = DefaultChunkSize)
                         (implicit F: Monad[F], C: Catchable[F], driver: ServletDriver[F]) extends HttpServlet with Logging {
  private[this] var serverSoftware: ServerSoftware = _

  override def init(config: ServletConfig) {
    serverSoftware = ServerSoftware(config.getServletContext.getServerInfo)
  }

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    val request = toRequest(servletRequest)
    val ctx = servletRequest.startAsync()
    driver.runAsync(handle(ctx, request), ctx.complete())
  }

  protected def handle(ctx: AsyncContext, request: Request[F])(): F[Unit] = {
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    service(request).flatMap { response =>
      servletResponse.setStatus(response.prelude.status.code, response.prelude.status.reason)
      for (header <- response.prelude.headers)
        servletResponse.addHeader(header.name, header.value)
      response.body.map(_.toArray)
    }.to(driver.responseBodySink(servletResponse))
     .handle { case NonFatal(e) =>
      logger.error("Error handling request", e)
      Process.emit(servletResponse.sendError(500))
    }.run
  }

  protected def toRequest(req: HttpServletRequest): Request[F] = {
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
    val body = driver.requestBody(req, chunkSize)
    Request(prelude, body)
  }

  protected def toHeaders(req: HttpServletRequest): HttpHeaders = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield HttpHeaders.RawHeader(name, value)
    HttpHeaders(headers.toSeq : _*)
  }
}

object Http4sServlet {
  private[servlet] val DefaultChunkSize = Http4sConfig.getInt("org.http4s.servlet.default-chunk-size")
}