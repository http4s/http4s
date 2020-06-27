/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.servlet

import cats.effect._
import cats.implicits._
import io.chrisdavenport.vault._
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}
import org.http4s._
import org.http4s.headers.`Transfer-Encoding`
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.server.SecureSession
import org.http4s.server.ServerRequestKeys
import org.log4s.getLogger

abstract class Http4sServlet[F[_]](service: HttpApp[F], servletIo: ServletIo[F])(implicit
    F: Effect[F])
    extends HttpServlet {
  protected val logger = getLogger

  // micro-optimization: unwrap the service and call its .run directly
  protected val serviceFn: Request[F] => F[Response[F]] = service.run

  protected var servletApiVersion: ServletApiVersion = _
  private[this] var serverSoftware: ServerSoftware = _

  object ServletRequestKeys {
    val HttpSession: Key[Option[HttpSession]] = Key.newKey[IO, Option[HttpSession]].unsafeRunSync
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
      bodyWriter: BodyWriter[F]
  ): F[Unit] = {
    val response = Response[F](Status.BadRequest).withEntity(parseFailure.sanitized)
    renderResponse(response, servletResponse, bodyWriter)
  }

  protected def renderResponse(
      response: Response[F],
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]
  ): F[Unit] =
    // Note: the servlet API gives us no undeprecated method to both set
    // a body and a status reason.  We sacrifice the status reason.
    F.delay {
      servletResponse.setStatus(response.status.code)
      for (header <- response.headers.toList if header.isNot(`Transfer-Encoding`))
        servletResponse.addHeader(header.name.toString, header.value)
    }.attempt
      .flatMap {
        case Right(()) => bodyWriter(response)
        case Left(t) =>
          response.body.drain.compile.drain.handleError {
            case t2 => logger.error(t2)("Error draining body")
          } *> F.raiseError(t)
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
    } yield Request(
      method = method,
      uri = uri,
      httpVersion = version,
      headers = toHeaders(req),
      body = servletIo.reader(req),
      attributes = Vault.empty
        .insert(Request.Keys.PathInfoCaret, getPathInfoIndex(req, uri))
        .insert(
          Request.Keys.ConnectionInfo,
          Request.Connection(
            local = InetSocketAddress.createUnresolved(req.getLocalAddr, req.getLocalPort),
            remote = InetSocketAddress.createUnresolved(req.getRemoteAddr, req.getRemotePort),
            secure = req.isSecure
          )
        )
        .insert(Request.Keys.ServerSoftware, serverSoftware)
        .insert(ServletRequestKeys.HttpSession, Option(req.getSession(false)))
        .insert(
          ServerRequestKeys.SecureSession,
          (
            Option(req.getAttribute("javax.servlet.request.ssl_session_id").asInstanceOf[String]),
            Option(req.getAttribute("javax.servlet.request.cipher_suite").asInstanceOf[String]),
            Option(req.getAttribute("javax.servlet.request.key_size").asInstanceOf[Int]),
            Option(
              req
                .getAttribute("javax.servlet.request.X509Certificate")
                .asInstanceOf[Array[X509Certificate]]))
            .mapN(SecureSession.apply)
        )
        .insert(Request.Keys.ServerSoftware, serverSoftware)
        .insert(ServletRequestKeys.HttpSession, Option(req.getSession(false)))
        .insert(
          ServerRequestKeys.SecureSession,
          (
            Option(req.getAttribute("javax.servlet.request.ssl_session_id").asInstanceOf[String]),
            Option(req.getAttribute("javax.servlet.request.cipher_suite").asInstanceOf[String]),
            Option(req.getAttribute("javax.servlet.request.key_size").asInstanceOf[Int]),
            Option(
              req
                .getAttribute("javax.servlet.request.X509Certificate")
                .asInstanceOf[Array[X509Certificate]]))
            .mapN(SecureSession.apply)
        )
    )

  private def getPathInfoIndex(req: HttpServletRequest, uri: Uri) = {
    val pathInfo =
      Uri.Path
        .fromString(req.getContextPath)
        .concat(Uri.Path.fromString(req.getServletPath))
    uri.path
      .indexOf(pathInfo)
      .getOrElse(-1)
  }

  protected def toHeaders(req: HttpServletRequest): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)
    Headers(headers.toList)
  }
}
