package org.http4s
package servlet

import java.net.InetSocketAddress
import javax.servlet._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import org.http4s.headers.`Transfer-Encoding`
import org.http4s.server._
import org.log4s.getLogger

import scala.collection.JavaConverters._

class BlockingHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    servletIo: BlockingServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: Effect[F])
    extends HttpServlet {
  private[this] val logger = getLogger

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

    serverSoftware = ServerSoftware(servletContext.getServerInfo)
  }

  override def service(
      servletRequest: HttpServletRequest,
      servletResponse: HttpServletResponse): Unit =
    try {
      val bodyWriter = servletIo.initWriter(servletResponse)

      val render = toRequest(servletRequest).fold(
        onParseFailure(_, servletResponse, bodyWriter),
        handleRequest(_, servletResponse, bodyWriter)
      )

      F.runAsync(render) {
          case Right(_) => Sync[IO].unit
          case Left(t) => Sync[IO].delay(errorHandler(servletResponse)(t))
        }
        .unsafeRunSync()
    } catch errorHandler(servletResponse)

  private def onParseFailure(
      parseFailure: ParseFailure,
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] = {
    val response =
      F.pure(
        Response[F](Status.BadRequest)
          .withEntity(parseFailure.sanitized))
    renderResponse(response, servletResponse, bodyWriter)
  }

  private def handleRequest(
      request: Request[F],
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] = {
    // Note: We're catching silly user errors in the lift => flatten.
    val response = serviceFn(request)
      .getOrElse(Response.notFound)
      .recoverWith(serviceErrorHandler(request))

    renderResponse(response, servletResponse, bodyWriter)
  }

  private def renderResponse(
      response: F[Response[F]],
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] =
    response.flatMap { resp =>
      // Note: the servlet API gives us no undeprecated method to both set
      // a body and a status reason.  We sacrifice the status reason.
      F.delay {
          servletResponse.setStatus(resp.status.code)
          for (header <- resp.headers if header.isNot(`Transfer-Encoding`))
            servletResponse.addHeader(header.name.toString, header.value)
        }
        .attempt
        .flatMap {
          case Right(()) => bodyWriter(resp)
          case Left(t) =>
            resp.body.drain.compile.drain.handleError {
              case t2 => logger.error(t2)("Error draining body")
            } *> F.raiseError(t)
        }
    }

  private def errorHandler(servletResponse: HttpServletResponse): PartialFunction[Throwable, Unit] = {
    case t: Throwable if servletResponse.isCommitted =>
      logger.error(t)("Error processing request after response was committed")

    case t: Throwable =>
      logger.error(t)("Error processing request")
      val response = F.pure(Response[F](Status.InternalServerError))
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      val render = renderResponse(response, servletResponse, NullBodyWriter)
      F.runAsync(render)(_ => IO.unit).unsafeRunSync()
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

object BlockingHttp4sServlet {
  def apply[F[_]: Effect](service: HttpRoutes[F]): BlockingHttp4sServlet[F] =
    new BlockingHttp4sServlet[F](
      service,
      BlockingServletIo(DefaultChunkSize),
      DefaultServiceErrorHandler
    )
}
