package org.http4s
package servlet

import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import javax.servlet._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import fs2.{Strategy, Task}
import org.http4s.batteries._
import org.http4s.headers.`Transfer-Encoding`
import org.http4s.server._
import org.log4s.getLogger

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

class Http4sServlet(service: HttpService,
                    asyncTimeout: Duration = Duration.Inf,
                    threadPool: ExecutorService,
                    private[this] var servletIo: ServletIo = BlockingServletIo(DefaultChunkSize))
  extends HttpServlet
{
  private[this] val logger = getLogger

  private val asyncTimeoutMillis = if (asyncTimeout.isFinite()) asyncTimeout.toMillis else -1 // -1 == Inf

  private[this] var serverSoftware: ServerSoftware = _

  // micro-optimization: unwrap the service and call its .run directly
  private[this] val serviceFn = service.run

  override def init(config: ServletConfig): Unit = {
    val servletContext = config.getServletContext
    val servletApiVersion = ServletApiVersion(servletContext)
    logger.info(s"Detected Servlet API version $servletApiVersion")

    verifyServletIo(servletApiVersion)
    logServletIo()
    serverSoftware = ServerSoftware(servletContext.getServerInfo)
  }

  // TODO This is a dodgy check.  It will have already triggered class loading of javax.servlet.WriteListener.
  // Remove when we can break binary compatibility.
  private def verifyServletIo(servletApiVersion: ServletApiVersion): Unit = servletIo match {
    case NonBlockingServletIo(chunkSize) if servletApiVersion < ServletApiVersion(3, 1) =>
      logger.warn("Non-blocking servlet I/O requires Servlet API >= 3.1. Falling back to blocking I/O.")
      servletIo = BlockingServletIo(chunkSize)
    case _ => // cool
  }

  private def logServletIo(): Unit = logger.info(servletIo match {
    case BlockingServletIo(chunkSize) => s"Using blocking servlet I/O with chunk size $chunkSize"
    case NonBlockingServletIo(chunkSize) => s"Using non-blocking servlet I/O with chunk size $chunkSize"
  })

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): Unit =
    try {
      val ctx = servletRequest.startAsync()
      ctx.setTimeout(asyncTimeoutMillis)
      // Must be done on the container thread for Tomcat's sake when using async I/O.
      val bodyWriter = servletIo.initWriter(servletResponse)
      toRequest(servletRequest).fold(
        onParseFailure(_, servletResponse, bodyWriter),
        handleRequest(ctx, _, bodyWriter)
      ).unsafeRunAsync {
        case Right(()) => ctx.complete()
        case Left(t) => errorHandler(servletRequest, servletResponse)(t)
      }
    }
    catch errorHandler(servletRequest, servletResponse)

  private def onParseFailure(parseFailure: ParseFailure,
                             servletResponse: HttpServletResponse,
                             bodyWriter: BodyWriter): Task[Unit] = {
    val response = Response(Status.BadRequest).withBody(parseFailure.sanitized)
    renderResponse(response, servletResponse, bodyWriter)
  }

  private def handleRequest(ctx: AsyncContext,
                            request: Request,
                            bodyWriter: BodyWriter): Task[Unit] = {
    ctx.addListener(new AsyncTimeoutHandler(request, bodyWriter))
    val response = Task.start(serviceFn(request))(Strategy.fromExecutor(threadPool)).flatten
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    renderResponse(response, servletResponse, bodyWriter)
  }

  private class AsyncTimeoutHandler(request: Request, bodyWriter: BodyWriter) extends AbstractAsyncListener {
    override def onTimeout(event: AsyncEvent): Unit = {
      val ctx = event.getAsyncContext
      val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
      if (!servletResponse.isCommitted) {
        val response = Response(Status.InternalServerError).withBody("Service timed out.")
        renderResponse(response, servletResponse, bodyWriter).unsafeRun
      }
      else {
        val servletRequest = ctx.getRequest.asInstanceOf[HttpServletRequest]
        logger.warn(s"Async context timed out, but response was already committed: ${request.method} ${request.uri.path}")
      }
      ctx.complete()
    }
  }

  private def renderResponse(response: Task[MaybeResponse],
                             servletResponse: HttpServletResponse,
                             bodyWriter: BodyWriter): Task[Unit] =
    response.flatMap { maybeResponse =>
      val r = maybeResponse.orNotFound
      // Note: the servlet API gives us no undeprecated method to both set
      // a body and a status reason.  We sacrifice the status reason.
      servletResponse.setStatus(r.status.code)
      for (header <- r.headers if header.isNot(`Transfer-Encoding`))
        servletResponse.addHeader(header.name.toString, header.value)
      bodyWriter(r)
    }

  private def errorHandler(servletRequest: ServletRequest, servletResponse: HttpServletResponse): PartialFunction[Throwable, Unit] = {
    case t: Throwable if servletResponse.isCommitted =>
     logger.error(t)("Error processing request after response was committed")

    case t: Throwable =>
      logger.error(t)("Error processing request")
      val response = Task.now(Response(Status.InternalServerError))
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      renderResponse(response, servletResponse, NullBodyWriter).unsafeRun
      if (servletRequest.isAsyncStarted)
        servletRequest.getAsyncContext.complete()
  }

  private def toRequest(req: HttpServletRequest): ParseResult[Request] =
    for {
      method <- Method.fromString(req.getMethod)
      uri <- Uri.requestTarget(Option(req.getQueryString).map { q => s"${req.getRequestURI}?$q" }.getOrElse(req.getRequestURI))
      version <- HttpVersion.fromString(req.getProtocol)
    } yield Request(
      method = method,
      uri = uri,
      httpVersion = version,
      headers = toHeaders(req),
      body = servletIo.reader(req),
      attributes = AttributeMap(
        Request.Keys.PathInfoCaret(req.getContextPath.length + req.getServletPath.length),
        Request.Keys.ConnectionInfo(Request.Connection(
          InetSocketAddress.createUnresolved(req.getRemoteAddr, req.getRemotePort),
          InetSocketAddress.createUnresolved(req.getLocalAddr, req.getLocalPort),
          req.isSecure
        )),
        Request.Keys.ServerSoftware(serverSoftware)
      )
    )

  private def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)
    Headers(headers.toSeq : _*)
  }
}
