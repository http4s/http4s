package org.http4s
package grizzly

import org.glassfish.grizzly.http.server.{Response,Request=>GrizReq,HttpHandler}
import play.api.libs.iteratee.{Enumerator, Input, Step, Iteratee}

import java.net.InetAddress
import scala.collection.JavaConverters._
import concurrent.{Future, ExecutionContext}

/**
 * @author Bryce Anderson
 */

class Http4sGrizzly(route: Route, chunkSize: Int = 32 * 1024)(implicit executor: ExecutionContext = ExecutionContext.global) extends HttpHandler {

  override def service(req: GrizReq, resp: Response) {
    resp.suspend()  // Suspend the response until we close it

    val request = toRequest(req)
    val handler:Future[Responder[Chunk]] = route.lift(request).getOrElse(
    Future.successful(Responder(statusLine = StatusLine.NotFound, body = Enumerator(s"${request.pathInfo} Not Found.".getBytes)))
    )


    // fold on the second one
    handler.onSuccess { case responder =>
      renderResponse(responder, resp)
    }
  }

  protected def renderResponse(responder: Responder[Chunk], resp: Response) {
    for (header <- responder.headers) {
      resp.addHeader(header.name, header.value)
    }
    val it = Iteratee.foreach[Chunk] { chunk =>
      resp.getOutputStream.write(chunk)
      resp.getOutputStream.flush()
    }
    responder.body.run(it).onComplete {
      case _ => resp.resume
    }
  }

  protected def toRequest(req: GrizReq): Request[Chunk] = {
    val input = req.getNIOInputStream
    Request(
      requestMethod = Method(req.getMethod.toString),

      scriptName = req.getContextPath, // + req.getServletPath,
      pathInfo = Option(req.getPathInfo).getOrElse(""),
      queryString = Option(req.getQueryString).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol.getProtocolString),
      headers = toHeaders(req),
      body = new BodyEnumerator(input, chunkSize),
      urlScheme = UrlScheme(req.getScheme),
      serverName = req.getServerName,
      serverPort = req.getServerPort,
      serverSoftware = ServerSoftware(req.getServerName),
      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(req: GrizReq): Headers = {
    val headers = for {
      name <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield HttpHeaders.RawHeader(name, value)
    Headers(headers.toSeq : _*)
  }
}
