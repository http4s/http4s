package org.http4s
package servlet

import java.util.concurrent.ExecutorService

import server._

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.net.InetAddress

import scala.collection.JavaConverters._
import javax.servlet.{ServletConfig, AsyncContext}

import Http4sServlet._
import util.CaseInsensitiveString._

import scala.concurrent.duration.Duration
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.io._
import scalaz.{-\/, \/-}
import scala.util.control.NonFatal
import org.parboiled2.ParseError
import org.log4s.getLogger

class Http4sServlet(service: HttpService,
                    asyncTimeout: Duration = Duration.Inf,
                    chunkSize: Int = DefaultChunkSize,
                    threadPool: ExecutorService = Strategy.DefaultExecutorService)
            extends HttpServlet
{
  private[this] val logger = getLogger

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
        logger.info(t)("Error during processing phase of request")
        response.sendError(HttpServletResponse.SC_BAD_REQUEST)

      case _ =>
        logger.error(t)("Error processing request")
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }
    else logger.error(t)("Error processing request")

  }

  private def handle(request: Request, ctx: AsyncContext): Unit = {
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    Task.fork {
      service(request).flatMap {
        case Some(response) =>
          servletResponse.setStatus(response.status.code, response.status.reason)
          for (header <- response.headers)
            servletResponse.addHeader(header.name.toString, header.value)
          val out = servletResponse.getOutputStream
          val isChunked = response.isChunked
          response.body.map { chunk =>
            out.write(chunk.toArray)
            if (isChunked) servletResponse.flushBuffer()
        }.run

        case None => ResponseBuilder.notFound(request)
      }
    }(threadPool).runAsync {
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
      uri <- Uri.fromString(req.getRequestURI)
      version <- HttpVersion.fromString(req.getProtocol)
    } yield Request(
      method = method,
      uri = uri,
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
  private[servlet] val DefaultChunkSize = 4096
}