package org.http4s
package servlet

import java.util.concurrent.atomic.AtomicReference

import scodec.bits.ByteVector
import server._

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.net.InetAddress

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import javax.servlet.{ ReadListener, ServletConfig, AsyncContext }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scalaz.concurrent.{Actor, Task}
import scalaz.stream.Cause.{End, Terminated}
import scalaz.stream.io._
import scalaz.{\/, -\/, \/-}
import scala.util.control.NonFatal
import org.parboiled2.ParseError
import com.typesafe.scalalogging.slf4j.LazyLogging

class Http4sServlet(service: HttpService,
                    asyncTimeout: Duration = Duration.Inf,
                    chunkSize: Int = 4096)
            extends HttpServlet with LazyLogging
{
  import Http4sServlet._

  private val asyncTimeoutMillis = if (asyncTimeout.isFinite) asyncTimeout.toMillis else -1  // -1 == Inf

  private[this] var serverSoftware: ServerSoftware = _
  private[this] var inputStreamReader: BodyReader = _

  override def init(config: ServletConfig) {
    val servletContext = config.getServletContext
    serverSoftware = ServerSoftware(servletContext.getServerInfo)
    val servletApiVersion = ServletApiVersion(servletContext)
    logger.info(s"Detected Servlet API version ${servletApiVersion}")

    inputStreamReader = if (servletApiVersion >= ServletApiVersion(3, 1))
      asyncInputStreamReader(chunkSize)
    else
      syncInputStreamReader(chunkSize)
  }


  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    try {
      val ctx = servletRequest.startAsync()
      toRequest(servletRequest) match {
        case -\/(e) =>
          // TODO make more http4sy
          servletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.sanitized)
        case \/-(req) =>
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
      uri <- Uri.fromString(req.getRequestURI)
      version <- HttpVersion.fromString(req.getProtocol)
    } yield Request(
      method = method,
      uri = uri,
      httpVersion = version,
      headers = toHeaders(req),
      body = inputStreamReader(req),
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

object Http4sServlet extends LazyLogging {
  import scalaz.stream.Process
  import scalaz.concurrent.Task

  private[servlet] val DefaultChunkSize = Http4sConfig.getInt("org.http4s.servlet.default-chunk-size")

  private type BodyReader = HttpServletRequest => EntityBody
  private type CB = Throwable\/ByteVector => Unit

  private def asyncInputStreamReader(chunkSize: Int)(req: HttpServletRequest): EntityBody = {
    val in = req.getInputStream

    var buff: Array[Byte] = null

    @volatile var callbacks: List[CB] = Nil

    // will always be called within a lock.synchronized block
    def doRead(cb: CB): Unit = {
      if (buff == null) buff = new Array[Byte](chunkSize)
      val len = in.read(buff)

      val buffer = if (len == chunkSize) {
        val b = ByteVector.view(buff)
        buff = null
        b
      } else if (len > 0) {
        // Need to truncate the array
        val b2 = new Array[Byte](len)
        System.arraycopy(buff, 0, b2, 0, len)
        ByteVector.view(b2)
      } else ByteVector.empty

      if (callbacks.nonEmpty) {
        callbacks.foreach(_(-\/(Terminated(End))))
        callbacks = Nil
      }

      cb(\/-(buffer))
    }

    if (in.isFinished) Process.halt
    else {
      case object DataAvailable
      case object AllDataRead

      val actor = Actor.actor[Any] {
        case DataAvailable =>
          if (callbacks != null && callbacks.nonEmpty) {
            val cb = callbacks.head
            callbacks = callbacks.tail
            doRead(cb)
          }

        case AllDataRead =>
          logger.debug("ReadListener signaled completion")
          if (callbacks != null && callbacks.nonEmpty) {
            callbacks.foreach(_(\/-(ByteVector.empty)))
          }
          callbacks = null

        case t: Throwable =>
          logger.error("Error during Servlet Async Read", t)
          if (callbacks != null && callbacks.nonEmpty) {
            callbacks.foreach(_(-\/(t)))
            callbacks = null
          }

        case cb: CB =>
          if (in.isFinished || callbacks == null) {
            logger.debug("Finished.")
            cb(-\/(Terminated(End)))
          }
          else if (in.isReady) {
            doRead(cb)
          }
          else {
            callbacks = cb::callbacks
          }
      }

      // Initialized the listener
      in.setReadListener(new ReadListener {
        override def onError(t: Throwable): Unit = actor ! t
        override def onDataAvailable(): Unit = actor ! DataAvailable
        override def onAllDataRead(): Unit = actor ! AllDataRead
      })

      Process.repeatEval(Task.async[ByteVector] { actor ! _ })
    }
  }

  private def syncInputStreamReader(chunkSize: Int)(req: HttpServletRequest): EntityBody =
    chunkR(req.getInputStream).map(_(chunkSize)).eval
}
