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

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
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
    val lock: AtomicReference[List[CB]] = new AtomicReference(Nil)

    def doRead(): Unit = {
      logger.debug("Reading data.")
      val cbs = lock.getAndSet(Nil)
      if (cbs.nonEmpty) {
        val cb = cbs.head
        var buffers = ByteVector.empty
        var buff = new Array[Byte](chunkSize)
        var len = 0

        while(in.isReady) {  // Read buffers until we empty the queue
          len = in.read(buff)
          if (len == chunkSize) {
            buffers = buffers ++ ByteVector.view(buff)
            buff = new Array[Byte](chunkSize)
          }
          else if (len > 0) {    // Need to truncate the array
            val b2 = new Array[Byte](len)
            System.arraycopy(buff, 0, b2, 0, len)
            buffers = buffers ++ ByteVector.view(b2)
          }
        }

        cb(\/-(buffers))

        val xs = cbs.tail
        if (xs.nonEmpty) xs.foreach(_(\/-(ByteVector.empty))) // just tell them to read again
      }
    }

    if (in.isFinished) Process.halt
    else {
      // Initialized the listener
      in.setReadListener(new ReadListener {
        override def onError(t: Throwable): Unit = {
          logger.error("Error during Servlet Async Read", t)

          val cbs = lock.getAndSet(null)
          if (cbs.nonEmpty) cbs.foreach(_(-\/(t)))
        }

        override def onDataAvailable(): Unit = doRead()

        override def onAllDataRead(): Unit =  {
          logger.debug("ReadListener signaled completion")
          val cbs = lock.getAndSet(null)
          if (cbs.nonEmpty) cbs.foreach(_(-\/(Terminated(End))))
        }
      })


      val go = Task.async { cb: CB =>
        @tailrec
        def go(): Unit = lock.get match { // submit the callback
          case null => cb(-\/(Terminated(End))) // over
          case xs   => if (!lock.compareAndSet(xs, cb::xs)) go()
        }

        if (in.isFinished) cb(-\/(Terminated(End)))
        else {
          go()
          if (in.isReady) doRead()
        }
      }

      Process.repeatEval(go)
    }
  }

  private def syncInputStreamReader(chunkSize: Int)(req: HttpServletRequest): EntityBody =
    chunkR(req.getInputStream).map(_(chunkSize)).eval
}