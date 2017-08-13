package org.http4s

import java.io._
import java.net.URL
import java.nio.file.{Path, StandardOpenOption}
import java.time.Instant

import cats.data._
import fs2._
import fs2.Stream._
import fs2.interop.cats._
import fs2.io._
import fs2.io.file.{FileHandle, pulls}
import fs2.util.Suspendable
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.http4s.util.ByteVectorChunk
import org.log4s.getLogger
import scodec.bits.ByteVector

// TODO: consider using the new scalaz.stream.nio.file operations
object StaticFile {
  private[this] val logger = getLogger

  val DefaultBufferSize = 10240

  def fromString(url: String, req: Option[Request] = None): OptionT[Task, Response] = {
    fromFile(new File(url), req)
  }

  def fromResource(name: String, req: Option[Request] = None, preferGzipped: Boolean = false): OptionT[Task, Response] = {
    val tryGzipped = preferGzipped && req.flatMap(_.headers.get(`Accept-Encoding`)).exists { acceptEncoding =>
      acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(ContentCoding.`x-gzip`)
    }

    val gzUrl: OptionT[Task, URL] =
      if (tryGzipped) OptionT.fromOption(Option(getClass.getResource(name + ".gz")))      else OptionT.none
    gzUrl.flatMap { url =>
      // Guess content type from the name without ".gz"
      val contentType = nameToContentType(name)
      val headers = `Content-Encoding`(ContentCoding.gzip) :: contentType.toList
      fromURL(url, req).map(_.removeHeader(`Content-Type`).putHeaders(headers: _*))
    } orElse OptionT.fromOption[Task](Option(getClass.getResource(name))).flatMap(fromURL(_, req))
  }

  def fromURL(url: URL, req: Option[Request] = None): OptionT[Task, Response] = OptionT.liftF(Task.delay {
    val lastmod = Instant.ofEpochMilli(url.openConnection.getLastModified)
    val expired = req
      .flatMap(_.headers.get(`If-Modified-Since`)).forall(_.date.compareTo(lastmod) < 0)

    if (expired) {
      val contentType = nameToContentType(url.getPath)
      val headers = Headers(`Last-Modified`(lastmod) :: contentType.toList)

      Response(
        headers = headers,
        body    = readInputStream[Task](Task.delay(url.openStream), DefaultBufferSize)
          // These chunks wrap a mutable array, and we might be buffering
          // or processing them concurrently later.  Convert to something
          // immutable here for safety.
          .mapChunks(c => ByteVectorChunk(ByteVector(c.toArray)))
      )
    } else Response(NotModified)
  })

  def fromFile(f: File, req: Option[Request] = None): OptionT[Task, Response] =
    fromFile(f, DefaultBufferSize, req)

  def fromFile(f: File, buffsize: Int, req: Option[Request]): OptionT[Task, Response] = {
    fromFile(f, 0, f.length(), buffsize, req)
  }

  def fromFile(f: File, start: Long, end: Long, buffsize: Int, req: Option[Request]): OptionT[Task, Response] = OptionT(Task.delay {
    if (f.isFile) {
      require (start >= 0 && end >= start && buffsize > 0, s"start: $start, end: $end, buffsize: $buffsize")

      val lastModified = Instant.ofEpochMilli(f.lastModified())

      // See if we need to actually resend the file
      val notModified = for {
        r   <- req
        h   <- r.headers.get(`If-Modified-Since`)
        exp  = h.date.compareTo(lastModified) < 0
        _    = logger.trace(s"Expired: $exp. Request age: ${h.date}, Modified: $lastModified")
        nm   = Response(NotModified) if !exp
      } yield nm

      notModified orElse {

        val (body, contentLength) =
          if (f.length() < end) (empty, 0L)
          else (fileToBody(f, start, end, buffsize), end - start)

        val contentType = nameToContentType(f.getName)
        val headers = `Last-Modified`(lastModified) ::
          `Content-Length`.fromLong(contentLength).fold(_ => contentType.toList, _ :: contentType.toList)

        val r = Response(
          headers = Headers(headers),
          body = body,
          attributes = AttributeMap.empty.put(staticFileKey, f)
        )

        logger.trace(s"Static file generated response: $r")
        Some(r)
      }
    } else {
      None
    }
  })

  private def fileToBody(f: File, start: Long, end: Long, buffsize: Int): EntityBody = {
    // Based on fs2 handling of files
    def readAllFromFileHandle[F[_]](chunkSize: Int, start: Long, end: Long)(h: FileHandle[F]): Pull[F, Byte, Unit] =
      _readAllFromFileHandle0(chunkSize, start, end)(h)

    def _readAllFromFileHandle0[F[_]](chunkSize: Int, offset: Long, end: Long)(h: FileHandle[F]): Pull[F, Byte, Unit] =
      for {
        res  <- Pull.eval(h.read(math.min(chunkSize, (end - offset).toInt), offset))
        next <- res.filter(_.nonEmpty)
          .fold[Pull[F, Byte, Unit]](Pull.done)(o => Pull.output(o) >> _readAllFromFileHandle0(chunkSize, offset + o.size, end)(h))
      } yield next

    def readAll[F[_]: Suspendable](path: Path, chunkSize: Int): Stream[F, Byte] =
      pulls.fromPath[F](path, List(StandardOpenOption.READ)).flatMap(readAllFromFileHandle(chunkSize, start, end)).close

    readAll[Task](f.toPath, DefaultBufferSize)
  }

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

  private[http4s] val staticFileKey = AttributeKey[File]
}
