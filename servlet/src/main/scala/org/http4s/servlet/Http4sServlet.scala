package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import play.api.libs.iteratee.{Done, Iteratee, Enumerator}
import java.net.InetAddress
import scala.collection.JavaConverters._
import concurrent.{ExecutionContext,Future}
import javax.servlet.{ServletConfig, AsyncContext}
import org.http4s.Status.{InternalServerError, NotFound}
import akka.util.ByteString

import Http4sServlet._
import scala.util.logging.Logged
import com.typesafe.scalalogging.slf4j.Logging

class Http4sServlet(route: Route, chunkSize: Int = DefaultChunkSize)
                   (implicit executor: ExecutionContext = ExecutionContext.global) extends HttpServlet with Logging {
  private[this] var serverSoftware: ServerSoftware = _

  override def init(config: ServletConfig) {
    serverSoftware = ServerSoftware(config.getServletContext.getServerInfo)
  }

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    val request = toRequest(servletRequest)
    val ctx = servletRequest.startAsync()
    executor.execute {
      new Runnable {
        def run() {
          handle(request, ctx)
        }
      }
    }
  }

  protected def handle(request: RequestPrelude, ctx: AsyncContext) {
    val servletRequest = ctx.getRequest.asInstanceOf[HttpServletRequest]
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    val parser = try {
      route.lift(request).getOrElse(Done(NotFound(request)))
    } catch { case t: Throwable => Done[Chunk, Responder](InternalServerError(t)) }
    val handler = parser.flatMap { responder =>
      servletResponse.setStatus(responder.prelude.status.code, responder.prelude.status.reason)
      for (header <- responder.prelude.headers)
        servletResponse.addHeader(header.name, header.value)
      val isChunked = responder.isChunked
      responder.body.transform(Iteratee.foreach {
        case BodyChunk(chunk) =>
          val out = servletResponse.getOutputStream
          out.write(chunk.toArray)
          if(isChunked) out.flush()
        case t: TrailerChunk =>
          log("The servlet adapter does not implement trailers. Silently ignoring.")
      })
    }
    Enumerator.fromStream(servletRequest.getInputStream, chunkSize)
      .map[Chunk](BodyChunk(_))
      .run(handler)
      .onComplete(_ => ctx.complete() )
  }

  protected def toRequest(req: HttpServletRequest): RequestPrelude = {
    RequestPrelude(
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