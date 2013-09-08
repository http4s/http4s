package org.http4s
package netty
package handlers

import io.netty.handler.codec.http
import io.netty.handler.ssl.SslHandler
import java.io.{UnsupportedEncodingException, FileNotFoundException, RandomAccessFile, File}
import javax.activation.MimetypesFileTypeMap
import org.http4s.HttpVersion
import scala.io.Codec
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import io.netty.channel._
import com.typesafe.scalalogging.slf4j.Logging
import io.netty.util.ReferenceCountUtil
import io.netty.handler.codec.TooLongFrameException
import io.netty.handler.stream.ChunkedFile
import io.netty.buffer.Unpooled

object StaticFileHandler {
  def serveFile(ctx: ChannelHandlerContext, request: http.HttpRequest, file: File, contentType: Option[String] = None) = {
    try {
      val raf = new RandomAccessFile(file, "r")
      val length = raf.length()
      val resp = new http.DefaultHttpResponse(http.HttpVersion.HTTP_1_1, http.HttpResponseStatus.OK)
      setDateHeader(resp)
      setCacheHeaders(resp, file, contentType)
      val ch = ctx.channel
      ch.writeAndFlush(resp)
      val future = if (ch.pipeline().get(classOf[SslHandler]) != null) {
        ch.write(new ChunkedFile(raf, 0, length, 8192))
      } else {
        // no ssl, zero-copy is a go
        val region = new DefaultFileRegion(raf.getChannel, 0, length)
        val fut = ch.write(region)
        fut.addListener(new ChannelProgressiveFutureListener {

          def operationComplete(future: ChannelProgressiveFuture) {}

          def operationProgressed(future: ChannelProgressiveFuture, amount: Long, total: Long) {
            printf("%s: %d / %d\n", file.getPath, amount, total)
          }

          def operationComplete(future: ChannelFuture) {
            region.release()
          }
        })
        fut
      }

      if (!http.HttpHeaders.isKeepAlive(request)) future.addListener(ChannelFutureListener.CLOSE)
    } catch {
      case _: FileNotFoundException => sendError(ctx, http.HttpResponseStatus.NOT_FOUND)
    }
  }

 private[this] val mimes = new MimetypesFileTypeMap() //(getClass.getResourceAsStream("/mime.types"))

  def HttpDateFormat = {
    val f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    f.setTimeZone(TimeZone.getTimeZone("GMT"))
    f
  }

  def HttpNow = HttpDateFormat.format(new Date)
  val HttpCacheSeconds = 60

  private def setDateHeader(response: http.HttpResponse) {
    response.headers.set(http.HttpHeaders.Names.DATE, HttpNow)
  }

  private def setCacheHeaders(response: http.HttpResponse, fileToCache: File, contentType: Option[String]) {
    response.headers.set(http.HttpHeaders.Names.CONTENT_TYPE, contentType getOrElse mimes.getContentType(fileToCache))
    response.headers.set(http.HttpHeaders.Names.EXPIRES, HttpNow)
    response.headers.set(http.HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HttpCacheSeconds)
    response.headers.set(http.HttpHeaders.Names.LAST_MODIFIED, HttpDateFormat.format(fileToCache.lastModified()))
    http.HttpHeaders.setContentLength(response, fileToCache.length())
  }

  def sendError(ctx: ChannelHandlerContext, status: http.HttpResponseStatus) {
    val response = new http.DefaultFullHttpResponse(HttpVersion.`Http/1.1`, status,
      Unpooled.wrappedBuffer(("Failure: " + status.toString + "\r\n").getBytes(Codec.UTF8.charSet)))
    response.headers.set(http.HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")

    // Close the connection as soon as the error message is sent.
    ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  def sendNotModified(ctx: ChannelHandlerContext) {
    val response = new http.DefaultHttpResponse(HttpVersion.`Http/1.1`, Status.NotModified)
    setDateHeader(response)

    // Close the connection as soon as the error message is sent.
    ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }
}

class StaticFileHandler(publicDirectory: String) extends ChannelInboundHandlerAdapter with Logging  {

  import StaticFileHandler._
  private def isModified(request: http.HttpRequest, file: File) = {
    val ifModifiedSince = request.headers.get(http.HttpHeaders.Names.IF_MODIFIED_SINCE)
    if (ifModifiedSince != null && ifModifiedSince.trim.nonEmpty) {
      val date = HttpDateFormat.parse(ifModifiedSince)
      val ifModifiedDateSeconds = date.getTime / 1000
      val fileLastModifiedSeconds = file.lastModified() / 1000
      ifModifiedDateSeconds == fileLastModifiedSeconds
    } else false
  }


  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any) {
    try {
      messageReceived(ctx, msg)
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }

  private def messageReceived(ctx: ChannelHandlerContext, msg: Any): Unit = msg match {
    case request: http.HttpRequest if request.getMethod != http.HttpMethod.GET =>
      StaticFileHandler.sendError(ctx, Status.MethodNotAllowed)
    case request: http.HttpRequest => {
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

  @throws(classOf[Exception])
  override def exceptionCaught(ctx: ChannelHandlerContext, e: Throwable) {
    e match {
      case ex: TooLongFrameException => StaticFileHandler.sendError(ctx, Status.BadRequest)
      case ex =>
        ex.printStackTrace()
        if (ctx.channel.isOpen()) StaticFileHandler.sendError(ctx, Status.InternalServerError)
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