package org.http4s
package servlet

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2.async
import java.net.InetSocketAddress
import javax.servlet._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}
import org.http4s.headers.`Transfer-Encoding`
import org.http4s.server._
import org.log4s.getLogger
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class Http4sServlet[F[_]](
    service: HttpService[F],
    asyncTimeout: Duration = Duration.Inf,
    implicit private[this] val executionContext: ExecutionContext = ExecutionContext.global,
    private[this] var servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: Effect[F])
    extends HttpServlet {
  private[this] val logger = getLogger(classOf[Http4sServlet[F]])

  private val asyncTimeoutMillis = if (asyncTimeout.isFinite()) asyncTimeout.toMillis else -1 // -1 == Inf

  private[this] var serverSoftware: ServerSoftware = _

  // micro-optimization: unwrap the service and call its .run directly
  private[this] val serviceFn = service.run

  object ServletRequestKeys {
    val HttpSession: AttributeKey[Option[HttpSession]] = AttributeKey[Option[HttpSession]]
  }

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
      logger.warn(
        "Non-blocking servlet I/O requires Servlet API >= 3.1. Falling back to blocking I/O.")
      servletIo = BlockingServletIo[F](chunkSize)
    case _ => // cool
  }

  private def logServletIo(): Unit =
    logger.info(servletIo match {
      case BlockingServletIo(chunkSize) => s"Using blocking servlet I/O with chunk size $chunkSize"
      case NonBlockingServletIo(chunkSize) =>
        s"Using non-blocking servlet I/O with chunk size $chunkSize"
    })

  override def service(
      servletRequest: HttpServletRequest,
      servletResponse: HttpServletResponse): Unit =
    try {
      val ctx = servletRequest.startAsync()
      ctx.setTimeout(asyncTimeoutMillis)
      // Must be done on the container thread for Tomcat's sake when using async I/O.
      val bodyWriter = servletIo.initWriter(servletResponse)
      async.unsafeRunAsync(
        toRequest(servletRequest).fold(
          onParseFailure(_, servletResponse, bodyWriter),
          handleRequest(ctx, _, bodyWriter)
        )) {
        case Right(()) =>
          IO(ctx.complete())
        case Left(t) =>
          IO(errorHandler(servletRequest, servletResponse)(t))
      }
    } catch errorHandler(servletRequest, servletResponse)

  private def onParseFailure(
      parseFailure: ParseFailure,
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] = {
    val response =
      Response[F](Status.BadRequest)
        .withBody(parseFailure.sanitized)
        .widen[MaybeResponse[F]]
    renderResponse(response, servletResponse, bodyWriter)
  }

  private def handleRequest(
      ctx: AsyncContext,
      request: Request[F],
      bodyWriter: BodyWriter[F]): F[Unit] = {
    ctx.addListener(new AsyncTimeoutHandler(request, bodyWriter))
    // TODO: This can probably be handled nicer with Effect
    val response = async.start {
      try serviceFn(request)
      // Handle message failures coming out of the service as failed tasks
        .recoverWith(serviceErrorHandler(request).andThen(_.widen[MaybeResponse[F]]))
      catch
      // Handle message failures _thrown_ by the service, just in case
      serviceErrorHandler(request).andThen(_.widen[MaybeResponse[F]])
    }.flatten

    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    renderResponse(response, servletResponse, bodyWriter)
  }

  private class AsyncTimeoutHandler(request: Request[F], bodyWriter: BodyWriter[F])
      extends AbstractAsyncListener {
    override def onTimeout(event: AsyncEvent): Unit = {
      val ctx = event.getAsyncContext
      val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
      async.unsafeRunAsync {
        if (!servletResponse.isCommitted) {
          val response =
            Response[F](Status.InternalServerError)
              .withBody("Service timed out.")
              .widen[MaybeResponse[F]]
          renderResponse(response, servletResponse, bodyWriter)
        } else {
          logger.warn(
            s"Async context timed out, but response was already committed: ${request.method} ${request.uri.path}")
          F.pure(())
        }
      } { _ =>
        ctx.complete()
        IO.unit
      }
    }
  }

  private def renderResponse(
      response: F[MaybeResponse[F]],
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] =
    response.flatMap { maybeResponse =>
      val r = maybeResponse.orNotFound
      // Note: the servlet API gives us no undeprecated method to both set
      // a body and a status reason.  We sacrifice the status reason.
      servletResponse.setStatus(r.status.code)
      for (header <- r.headers if header.isNot(`Transfer-Encoding`))
        servletResponse.addHeader(header.name.toString, header.value)
      bodyWriter(r)
    }

  private def errorHandler(
      servletRequest: ServletRequest,
      servletResponse: HttpServletResponse): PartialFunction[Throwable, Unit] = {
    case t: Throwable if servletResponse.isCommitted =>
      logger.error(t)("Error processing request after response was committed")

    case t: Throwable =>
      logger.error(t)("Error processing request")
      val response = F.pure(Response[F](Status.InternalServerError).asMaybeResponse)
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      async
        .unsafeRunAsync(renderResponse(response, servletResponse, NullBodyWriter)) { _ =>
          IO {
            if (servletRequest.isAsyncStarted)
              servletRequest.getAsyncContext.complete()
          }
        }
  }

  private def toRequest(req: HttpServletRequest): ParseResult[Request[F]] =
    for {
      method <- Method.fromString(req.getMethod)
      uri <- Uri.requestTarget(
        Option(req.getQueryString)
          .map { q =>
            s"${req.getRequestURI}?$q"
          }
          .getOrElse(req.getRequestURI))
      version <- HttpVersion.fromString(req.getProtocol)
    } yield
      Request(
        method = method,
        uri = uri,
        httpVersion = version,
        headers = toHeaders(req),
        body = servletIo.reader(req),
        attributes = AttributeMap(
          Request.Keys.PathInfoCaret(req.getContextPath.length + req.getServletPath.length),
          Request.Keys.ConnectionInfo(
            Request.Connection(
              InetSocketAddress.createUnresolved(req.getRemoteAddr, req.getRemotePort),
              InetSocketAddress.createUnresolved(req.getLocalAddr, req.getLocalPort),
              req.isSecure
            )),
          Request.Keys.ServerSoftware(serverSoftware),
          ServletRequestKeys.HttpSession(Option(req.getSession(false)))
        )
      )

  private def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)
    Headers(headers.toSeq: _*)
  }
}

object Http4sServlet {
  def apply[F[_]: Effect](
      service: HttpService[F],
      asyncTimeout: Duration = Duration.Inf,
      executionContext: ExecutionContext = ExecutionContext.global): Http4sServlet[F] =
    new Http4sServlet[F](
      service,
      asyncTimeout,
      executionContext,
      BlockingServletIo[F](DefaultChunkSize),
      DefaultServiceErrorHandler
    )
}
