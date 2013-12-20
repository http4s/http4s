package org.http4s
package util

import java.io.File
import java.util.Collections
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.nio.file.Files
import java.util.concurrent.ExecutorService

import scalaz.{\/-, -\/}
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process
import Process._

import org.joda.time.DateTime

import com.typesafe.scalalogging.slf4j.Logging

import org.http4s.Header.{`Last-Modified`, `Content-Length`, `Content-Type`}


/**
 * @author Bryce Anderson
 *         Created on 12/18/13
 */

object StaticFile extends Logging {

  val DEFAULT_BUFFSIZE = 10*1024    // 10KB

  def fromString(url: String)
                (implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] = {
    fromFile(new File(url))
  }

  def fromURL(url: URL)
             (implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] = {
    fromFile(new File(url.toURI))(es)
  }

  def fromFile(f: File)(implicit es: ExecutorService = Strategy.DefaultExecutorService): Option[Response] =
    fromFile(f, DEFAULT_BUFFSIZE)

  def fromFile(f: File, buffsize: Int)
           (implicit es: ExecutorService): Option[Response] = {
    if (f.isFile) {
      StaticFile.fromFile(f, 0, f.length(), buffsize)(es)
    }
    else None
  }

  def fromFile(f: File, start: Long, end: Long, buffsize: Int)
                        (implicit es: ExecutorService): Option[Response] = {

    if (start < 0 || end < start || buffsize <= 0)
      throw new Exception(s"start: $start, end: $end, buffsize: $buffsize")

    if (!f.isFile) return None

    val mimeheader = Option(Files.probeContentType(f.toPath)).flatMap { mime =>
      val parts = mime.split('/')
      if (parts.length == 2) {
        MediaType.getForKey( (parts(0), parts(1)) ).map(`Content-Type`(_))
      } else None
    }

    val len = f.length()

    val body = if (len < end) halt else fileToBody(f, start, end, buffsize)

    val lengthheader = `Content-Length`(if (len < end) 0 else (end - start).toInt)
    val lastmodified = `Last-Modified`(new DateTime(f.lastModified()))

    // See if we have a mime type or not
    val headers = HeaderCollection.apply(mimeheader match {
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
                (implicit es: ExecutorService): Process[Task, BodyChunk] = {

    val outer = Task {

      val ch = AsynchronousFileChannel.open(f.toPath, Collections.emptySet(), es)

      val buff = ByteBuffer.allocate(buffsize)
      var position = start

      val innerTask = Task.async[BodyChunk]{ cb =>
        // Check for ending condition
        if (!ch.isOpen) cb(-\/(End))

        else {
          val remaining = end - position
          if (buff.capacity() > remaining) buff.limit(remaining.toInt)

          ch.read(buff, position, null: Null, new CompletionHandler[Integer, Null] {
            def failed(exc: Throwable, attachment: Null) {
              logger.error("Static file NIO process failed", exc)
              ch.close()
              cb(-\/(exc))
            }

            def completed(count: Integer, attachment: Null) {
              logger.trace(s"Read $count bytes")
              buff.flip()

              // Don't make yet another copy unless we need to
              val c = if (buffsize == count) {
                BodyChunk(buff.array())
              } else BodyChunk(buff)

              buff.clear()
              position += count
              if (position >= end) ch.close()

              cb(\/-(c))
            }
          })
        }
      }

      val cleanup: Process[Task, Nothing] = await(Task[Unit]{
        logger.trace(s"Cleaning up file: ensuring ${f.toURI} is closed")
        if (ch.isOpen) ch.close()
      })()

      def go(c: BodyChunk): Process[Task, BodyChunk] = {
        Emit(c::Nil, await(innerTask)(go, cleanup, cleanup))
      }

      await(innerTask)(go)
    }

    await(outer)(identity)
  }

  private[http4s] val staticFileKey = AttributeKey.http4s[File]("staticFile")

}
