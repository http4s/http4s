package org.http4s

import java.io._
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels._
import java.time.Instant
import java.util.concurrent.ExecutorService

import fs2._
import fs2.Stream._
import fs2.io._
import org.http4s.Status._
import org.http4s.headers._
import org.log4s.getLogger

// TODO: consider using the new scalaz.stream.nio.file operations
object StaticFile {
  private[this] val logger = getLogger

  val DefaultBufferSize = 10240

  def fromString(url: String, req: Option[Request] = None)
                (implicit es: ExecutorService): Option[Response] = {
    fromFile(new File(url), req)
  }

  def fromResource(name: String, req: Option[Request] = None)
             (implicit es: ExecutorService): Option[Response] = {
    Option(getClass.getResource(name)).flatMap(fromURL(_, req))
  }

  def fromURL(url: URL, req: Option[Request] = None)
             (implicit es: ExecutorService): Option[Response] = {
    val lastmod = Instant.ofEpochMilli(url.openConnection.getLastModified())
    val expired = req
      .flatMap(_.headers.get(`If-Modified-Since`))
      .map(_.date.compareTo(lastmod) < 0)
      .getOrElse(true)

    if (expired) {
      val mime    = MediaType.forExtension(url.getPath.split('.').last)
      val headers = Headers.apply(
        mime.fold(List[Header](`Last-Modified`(lastmod)))
          (`Content-Type`(_)::`Last-Modified`(lastmod)::Nil))

      Some(Response(
        headers = headers,
        body    = readInputStream[Task](Task.delay(url.openStream), DefaultBufferSize)
      ))
    } else Some(Response(NotModified))
  }

  def fromFile(f: File, req: Option[Request] = None)(implicit es: ExecutorService): Option[Response] =
    fromFile(f, DefaultBufferSize, req)

  def fromFile(f: File, buffsize: Int, req: Option[Request])
           (implicit es: ExecutorService): Option[Response] = {
    fromFile(f, 0, f.length(), buffsize, req)
  }

  def fromFile(f: File, start: Long, end: Long, buffsize: Int, req: Option[Request])
                        (implicit es: ExecutorService): Option[Response] = {
    if (!f.isFile) return None

    require (start >= 0 && end >= start && buffsize > 0, s"start: $start, end: $end, buffsize: $buffsize")

    val lastModified = Instant.ofEpochMilli(f.lastModified())

    // See if we need to actually resend the file
    val notModified = for {
      r   <- req
      h   <- r.headers.get(`If-Modified-Since`)
      exp  = h.date.compareTo(lastModified) < 0
      _    = logger.trace(s"Expired: ${exp}. Request age: ${h.date}, Modified: $lastModified")
      nm   = Response(NotModified) if (!exp)
    } yield nm

    notModified orElse {

      val (body, contentLength) =
        if (f.length() < end) (empty, 0L)
        else (fileToBody(f, start, end, buffsize), (end - start).toLong)

      val contentType = {
        val name = f.getName()

        name.lastIndexOf('.') match {
          case -1 => None
          case  i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
        }
      }

      val hs = `Last-Modified`(lastModified) ::
               `Content-Length`(contentLength) ::
               contentType.toList

      val r = Response(
        headers = Headers(hs),
        body = body,
        attributes = AttributeMap.empty.put(staticFileKey, f)
      )

      logger.trace(s"Static file generated response: $r")
      Some(r)
  }}

  private def fileToBody(f: File, start: Long, end: Long, buffsize: Int)
                (implicit es: ExecutorService): EntityBody = {
    readInputStream[Task](Task.delay(new FileInputStream(f)), buffsize)
      .drop(start) // TODO this is sad if start is much bigger than 0
      .take(end - start)
    /*
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
            def failed(t: Throwable, attachment: Null): Unit = {
              logger.error(t)("Static file NIO process failed")
              ch.close()
              cb(-\/(t))
            }

            def completed(count: Integer, attachment: Null): Unit = {
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

      def go(c: ByteVector): EntityBody = {
        emit(c) ++ awaitOr(innerTask)(_ => cleanup)(go)
      }

      await(innerTask)(go)
    }

    await(outer)(identity)
     */
  }

  private[http4s] val staticFileKey = AttributeKey.http4s[File]("staticFile")
}
