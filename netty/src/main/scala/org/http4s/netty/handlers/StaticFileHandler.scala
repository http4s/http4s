package org.http4s
package netty
package handlers

import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.stream.ChunkedFile
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.frame.TooLongFrameException
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import java.io.{UnsupportedEncodingException, FileNotFoundException, RandomAccessFile, File}
import java.net.URLDecoder
import javax.activation.MimetypesFileTypeMap
import org.http4s.HttpVersion
import io.Codec
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import scala.util.Try

object StaticFileHandler {
  def serveFile(ctx: ChannelHandlerContext, request: HttpRequest, file: File, contentType: Option[String] = None) = {
    try {
      val raf = new RandomAccessFile(file, "r")
      val length = raf.length()
      val resp = new DefaultHttpResponse(HttpVersion.`Http/1.1`, Status.Ok)
      setDateHeader(resp)
      setCacheHeaders(resp, file, contentType)
      val ch = ctx.getChannel
      ch.write(resp)
      val future = if (ch.getPipeline.get(classOf[SslHandler]) != null) {
        ch.write(new ChunkedFile(raf, 0, length, 8192))
      } else {
        // no ssl, zero-copy is a go
        val region = new DefaultFileRegion(raf.getChannel, 0, length)
        val fut = ch.write(region)
        fut.addListener(new ChannelFutureProgressListener {
          def operationProgressed(future: ChannelFuture, amount: Long, current: Long, total: Long) {
            printf("%s: %d / %d (+%d)%n", file.getPath, current, total, amount)
          }

          def operationComplete(future: ChannelFuture) {
            region.releaseExternalResources()
          }
        })
        fut
      }

      if (!HttpHeaders.isKeepAlive(request)) future.addListener(ChannelFutureListener.CLOSE)
    } catch {
      case _: FileNotFoundException => sendError(ctx, HttpResponseStatus.NOT_FOUND)
    }
  }

  private[this] val mimes = new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))

  def HttpDateFormat = {
    val f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    f.setTimeZone(TimeZone.getTimeZone("GMT"))
    f
  }

  def HttpNow = HttpDateFormat.format(new Date)
  val HttpCacheSeconds = 60

  private def setDateHeader(response: HttpResponse) {
    response.setHeader(HttpHeaders.Names.DATE, HttpNow)
  }



  private def setCacheHeaders(response: HttpResponse, fileToCache: File, contentType: Option[String]) {
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType getOrElse mimes.getContentType(fileToCache))
    response.setHeader(HttpHeaders.Names.EXPIRES, HttpNow)
    response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HttpCacheSeconds)
    response.setHeader(HttpHeaders.Names.LAST_MODIFIED, HttpDateFormat.format(fileToCache.lastModified()))
    HttpHeaders.setContentLength(response, fileToCache.length())
  }

  def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus) {
    val response = new DefaultHttpResponse(HttpVersion.`Http/1.1`, status)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString + "\r\n", Codec.UTF8.charSet))

    // Close the connection as soon as the error message is sent.
    ctx.getChannel.write(response).addListener(ChannelFutureListener.CLOSE)
  }

  def sendNotModified(ctx: ChannelHandlerContext) {
    val response = new DefaultHttpResponse(HttpVersion.`Http/1.1`, Status.NotModified)
    setDateHeader(response)

    // Close the connection as soon as the error message is sent.
    ctx.getChannel.write(response).addListener(ChannelFutureListener.CLOSE)
  }
}

class StaticFileHandler(publicDirectory: String) extends SimpleChannelUpstreamHandler {

  import StaticFileHandler._
  private def isModified(request: HttpRequest, file: File) = {
    val ifModifiedSince = request.getHeader(HttpHeaders.Names.IF_MODIFIED_SINCE)
    if (ifModifiedSince != null && ifModifiedSince.trim.nonEmpty) {
      val date = HttpDateFormat.parse(ifModifiedSince)
      val ifModifiedDateSeconds = date.getTime / 1000
      val fileLastModifiedSeconds = file.lastModified() / 1000
      ifModifiedDateSeconds == fileLastModifiedSeconds
    } else false
  }

  @throws(classOf[Exception])
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case request: HttpRequest if request.getMethod != HttpMethod.GET =>
        StaticFileHandler.sendError(ctx, Status.MethodNotAllowed)
      case request: HttpRequest => {
        val path = sanitizeUri(request.getUri)
        if (path == null) {
          StaticFileHandler.sendError(ctx, Status.Forbidden)
        } else {
          val file = new File(path)
          if (file.isHidden || !file.exists()) StaticFileHandler.sendError(ctx, Status.NotFound)
          else if (!file.isFile) StaticFileHandler.sendError(ctx, Status.Forbidden)
          else {
            if (isModified(request, file)) {
              StaticFileHandler.sendNotModified(ctx)
            } else {
              StaticFileHandler.serveFile(ctx, request, file)
            }
          }
        }
      }
    }
  }

  @throws(classOf[Exception])
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    e.getCause match {
      case ex: TooLongFrameException => StaticFileHandler.sendError(ctx, Status.BadRequest)
      case ex =>
        ex.printStackTrace()
        if (e.getChannel.isConnected) StaticFileHandler.sendError(ctx, Status.InternalServerError)
    }
  }

  import scala.util.control.Exception.catching
  private def sanitizeUri(uri: String) = {
    val tryOrFallback = catching(classOf[UnsupportedEncodingException]).withApply(_ => uri.urlDecode(Codec.ISO8859))
    val decoded = tryOrFallback(uri.urlDecode())

    // Convert file separators.
    val converted = decoded.replace('/', File.separatorChar)

    val uf = new File(s"${sys.props("user.dir")}${File.separatorChar}$publicDirectory").getAbsolutePath
    val pth = s"${uf}${File.separatorChar}$converted"
    val f = new File(pth)
    val absPath = f.getAbsolutePath
    if (!(absPath startsWith uf)) null
    else absPath
  }


}