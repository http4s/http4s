package org.http4s.servlet

import java.net.InetSocketAddress
import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}

import cats.effect.Effect
import cats.implicits.{catsSyntaxEither => _, _}
import org.http4s._
import org.http4s.headers.`Transfer-Encoding`
import org.log4s.getLogger

import scala.collection.JavaConverters._

abstract class Http4sServlet[F[_]](service: HttpRoutes[F], servletIo: ServletIo[F])(
    implicit F: Effect[F])
    extends HttpServlet {
  protected val logger = getLogger

  // micro-optimization: unwrap the service and call its .run directly
  protected val serviceFn = service.run

  protected var servletApiVersion: ServletApiVersion = _
  private[this] var serverSoftware: ServerSoftware = _

  object ServletRequestKeys {
    val HttpSession: AttributeKey[Option[HttpSession]] = AttributeKey[Option[HttpSession]]
  }

  override def init(config: ServletConfig): Unit = {
    val servletContext = config.getServletContext
    servletApiVersion = ServletApiVersion(servletContext)
    logger.info(s"Detected Servlet API version $servletApiVersion")
    serverSoftware = ServerSoftware(servletContext.getServerInfo)
  }

  protected def onParseFailure(
      parseFailure: ParseFailure,
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] = {
    val response =
      F.pure(
        Response[F](Status.BadRequest)
          .withEntity(parseFailure.sanitized))
    renderResponse(response, servletResponse, bodyWriter)
  }

  protected def renderResponse(
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

  protected def toRequest(req: HttpServletRequest): ParseResult[Request[F]] =
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

  protected def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)
    Headers(headers.toSeq: _*)
  }
}
