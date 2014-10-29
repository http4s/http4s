package org.http4s

import java.io.File
import java.util.Collections
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import scalaz.stream.Cause.{End, Terminated}
import scalaz.{\/-, -\/}
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process
import Process._

import org.http4s.Header._
import org.http4s.Status.NotModified
import org.log4s.getLogger
import scodec.bits.ByteVector

object StaticFile {
  private[this] val logger = getLogger

  val DEFAULT_BUFFSIZE = Http4sConfig.getInt("org.http4s.staticfile.default-buffersize")    // 10KB

  def fromString(url: String, req: Option[Request] = None)
                (implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] = {
    fromFile(new File(url), req)
  }

  def fromResource(name: String, req: Option[Request] = None)
             (implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] = {
    val url = getClass.getResource(name)
    if (url != null) StaticFile.fromURL(url, req)(es)
    else None
  }

  def fromURL(url: URL, req: Option[Request] = None)
             (implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] = {
    if (url == null)
      throw new NullPointerException("url")

    fromFile(new File(url.toURI), req)(es)
  }

  def fromFile(f: File, req: Option[Request] = None)(implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] =
    fromFile(f, DEFAULT_BUFFSIZE, req)

  def fromFile(f: File, buffsize: Int, req: Option[Request])
           (implicit es: ExecutorService): Option[Response] = {
    if (f.isFile) {
      StaticFile.fromFile(f, 0, f.length(), buffsize, req)(es)
    }
    else None
  }

  def fromFile(f: File, start: Long, end: Long, buffsize: Int, req: Option[Request])
                        (implicit es: ExecutorService): Option[Response] = {

    if (f == null) throw new NullPointerException("File")

    if (start < 0 || end < start || buffsize <= 0)
      throw new Exception(s"start: $start, end: $end, buffsize: $buffsize")

    if (!f.isFile) return None

    val lastModified = f.lastModified()

//    // See if we need to actually resend the file
    if (req.isDefined) {
      req.get.headers.get(`If-Modified-Since`) match {
        case Some(h) =>
          val mod = DateTime(lastModified)
          val expired = h.date.compareTo(mod) < 0
          logger.trace(s"Expired: ${expired}. Request age: ${h.date}, Modified: $mod")
          if (!expired) {
            return Some(Response(NotModified))
          }

        case _ =>  // Just send it
      }
    }

    val lastmodified = `Last-Modified`(DateTime(lastModified))

    val mimeheader = Option(Files.probeContentType(f.toPath)).flatMap { mime =>
      val parts = mime.split('/')
      if (parts.length == 2) {
        MediaType.get( (parts(0), parts(1)) ).map(`Content-Type`(_))
      } else None
    }

    val len = f.length()
    val body = if (len < end) halt else fileToBody(f, start, end, buffsize)
    val lengthheader = `Content-Length`(if (len < end) 0 else (end - start).toInt)

    // See if we have a mime type or not
    val headers = Headers.apply(mimeheader match {
      case Some(h) =>  h::lastmodified::lengthheader::Nil
      case None    =>     lastmodified::lengthheader::Nil
    })

    val r = Response(
      headers = headers,
      body = body,
      attributes = AttributeMap.empty.put(staticFileKey, f)
    )

    logger.trace(s"Static file generated response: $r")
    Some(r)
  }

  private def fileToBody(f: File, start: Long, end: Long, buffsize: Int)
                (implicit es: ExecutorService): Process[Task, ByteVector] = {

    val outer = Task {

      val ch = AsynchronousFileChannel.open(f.toPath, Collections.emptySet(), es)

      val buff = ByteBuffer.allocate(buffsize)
      var position = start

      val innerTask = Task.async[ByteVector]{ cb =>
        // Check for ending condition
        if (!ch.isOpen) cb(-\/(Terminated(End)))

        else {
          val remaining = end - position
          if (buff.capacity() > remaining) buff.limit(remaining.toInt)

          ch.read(buff, position, null: Null, new CompletionHandler[Integer, Null] {
            def failed(t: Throwable, attachment: Null) {
              logger.error(t)("Static file NIO process failed")
              ch.close()
              cb(-\/(t))
            }

            def completed(count: Integer, attachment: Null) {
              logger.trace(s"Read $count bytes")
              buff.flip()

              // Don't make yet another copy unless we need to
              val c = if (buffsize == count) {
                ByteVector(buff.array())
              } else ByteVector(buff)

              buff.clear()
              position += count
              if (position >= end) ch.close()

              cb(\/-(c))
            }
          })
        }
      }

      val cleanup: Process[Task, Nothing] = eval_(Task[Unit]{
        logger.trace(s"Cleaning up file: ensuring ${f.toURI} is closed")
        if (ch.isOpen) ch.close()
      })

      def go(c: ByteVector): Process[Task, ByteVector] = {
        emit(c) ++ awaitOr(innerTask)(_ => cleanup)(go)
      }

      await(innerTask)(go)
    }

    await(outer)(identity)
  }

  private[http4s] val staticFileKey = AttributeKey.http4s[File]("staticFile")
}
