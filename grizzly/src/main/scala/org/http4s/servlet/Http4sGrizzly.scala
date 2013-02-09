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
    val handler = route(request)

    // First runAndCollect of the Enumerator
    val responderAndRemainingBody = runAndCollect(request.body |>> handler)

    // fold on the second one
    responderAndRemainingBody.onSuccess { case (responder,leftOvers) =>
      renderResponse(responder,
        leftOvers match {
          case Some(d) => Enumerator.enumInput(d)
          case None => Enumerator.enumInput(Input.Empty)
        },
        resp)
    }
  }

  /*
  Helper method that gets any returned input by the enumerator it can be stacked back onto the body
   */
  private[this] def runAndCollect(it: Future[Handler]): Future[(Responder,Option[Input[Chunk]])] = it.flatMap(_.fold({
    case Step.Done(a, d@Input.El(_)) => Future.successful((a,Some(d)))
    case Step.Done(a, _) => Future.successful((a,None))
    case Step.Cont(k) => k(Input.EOF).fold({
      case Step.Done(a1, d@Input.El(_)) => Future.successful((a1, Some(d)))
      case Step.Done(a1, _) => Future.successful((a1, None))
      case Step.Cont(_) => sys.error("diverging iteratee after Input.EOF")
      case Step.Error(msg, e) => sys.error(msg)
    })
    case Step.Error(msg, e) => sys.error(msg)
  }))

  protected def renderResponse(responder: Responder, preBodyEnum: Enumerator[Chunk], resp: Response) {
    for (header <- responder.headers) {
      resp.addHeader(header.name, header.value)
    }
    val it = Iteratee.foreach[Chunk] { chunk =>
      resp.getOutputStream.write(chunk)   // Would this be better as a buffer?
      resp.getOutputStream.flush()
    }
    (preBodyEnum >>> responder.body).run(it).onComplete {
      case _ => resp.resume
    }
  }

  protected def toRequest(req: GrizReq): Request = {
    val input = req.getNIOInputStream
    Request(
      requestMethod = Method(req.getMethod.toString),

      scriptName = req.getContextPath, // + req.getServletPath,
      pathInfo = Option(req.getPathInfo).getOrElse(""),
      queryString = Option(req.getQueryString).getOrElse(""),
      protocol = ServerProtocol(req.getProtocol.getProtocolString),
      headers = toHeaders(req),
      body = new org.http4s.test.BodyEnumerator(input, chunkSize),
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
    } yield Header(name, value)
    Headers(headers.toSeq : _*)
  }
}
