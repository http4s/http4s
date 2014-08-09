package org.http4s
package servlet

import server.{HttpService, ServerSoftware}

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.net.InetAddress

import scala.collection.JavaConverters._
import javax.servlet.{ServletConfig, AsyncContext}

import Http4sServlet._
import util.CaseInsensitiveString._

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.stream.io._
import scalaz.{-\/, \/-}
import scala.util.control.NonFatal
import org.parboiled2.ParseError
import com.typesafe.scalalogging.slf4j.LazyLogging

class Http4sServlet(service: HttpService, asyncTimeout: Duration = Duration.Inf, chunkSize: Int = DefaultChunkSize)
            extends HttpServlet with LazyLogging {

  private val asyncTimeoutMillis = if (asyncTimeout.isFinite) asyncTimeout.toMillis else -1  // -1 == Inf

  private[this] var serverSoftware: ServerSoftware = _

  override def init(config: ServletConfig) {
    serverSoftware = ServerSoftware(config.getServletContext.getServerInfo)
  }

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    try {
      val request = toRequest(servletRequest)
      request match {
        case -\/(e) =>
          // TODO make more http4sy
          servletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.sanitized)
        case \/-(req) =>
          val ctx = servletRequest.startAsync()
          ctx.setTimeout(asyncTimeoutMillis)
          handle(req, ctx)
      }
    } catch {
      case NonFatal(e) => handleError(e, servletResponse)
    }
  }

  private def handleError(t: Throwable, response: HttpServletResponse) {
    if (!response.isCommitted) t match {
      case ParseError(_, _) =>
        logger.info("Error during processing phase of request", t)
        response.sendError(HttpServletResponse.SC_BAD_REQUEST)

      case _ =>
        logger.error("Error processing request", t)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }
    else logger.error("Error processing request", t)

  }

  private def handle(request: Request, ctx: AsyncContext): Unit = {
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    Task.fork {
      service.applyOrElse(request, Status.NotFound(_: Request)).flatMap { response =>
        servletResponse.setStatus(response.status.code, response.status.reason)
        for (header <- response.headers)
          servletResponse.addHeader(header.name.toString, header.value)
        val out = servletResponse.getOutputStream
        val isChunked = response.isChunked
        response.body.map { chunk =>
          out.write(chunk.toArray)
          if (isChunked) servletResponse.flushBuffer()
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

  protected def toRequest(req: HttpServletRequest): ParseResult[Request] =
    for {
      method <- Method.fromString(req.getMethod)
      version <- HttpVersion.fromString(req.getProtocol)
    } yield Request(
      requestMethod = method,
      requestUri = Uri.fromString(req.getRequestURI).get,
      httpVersion = version,
      headers = toHeaders(req),
      body = chunkR(req.getInputStream).map(f => f(chunkSize)).eval,
      attributes = AttributeMap(
        Request.Keys.PathInfoCaret(req.getServletPath.length),
        Request.Keys.Remote(InetAddress.getByName(req.getRemoteAddr)),
        Request.Keys.ServerSoftware(serverSoftware)
      )
    )

  protected def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)
    Headers(headers.toSeq : _*)
  }
}

object Http4sServlet {
  private[servlet] val DefaultChunkSize = Http4sConfig.getInt("org.http4s.servlet.default-chunk-size")
}