package org.http4s

import java.io._
import java.net.URL
import java.nio.file.{Path, StandardOpenOption}
import java.time.Instant

import cats.effect.Sync
import fs2.Stream._
import fs2._
import fs2.io._
import fs2.io.file.{FileHandle, pulls}
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.log4s.getLogger

// TODO: consider using the new scalaz.stream.nio.file operations
object StaticFile {
  private[this] val logger = getLogger

  val DefaultBufferSize = 10240

  def fromString[F[_]: Sync](url: String, req: Option[Request[F]] = None): Option[Response[F]] = {
    fromFile(new File(url), req)
  }

  def fromResource[F[_]: Sync](name: String, req: Option[Request[F]] = None): Option[Response[F]] = {
    Option(getClass.getResource(name)).flatMap(fromURL(_, req))
  }

  def fromURL[F[_]](url: URL, req: Option[Request[F]] = None)(implicit F: Sync[F]): Option[Response[F]] = {
    val lastmod = Instant.ofEpochMilli(url.openConnection.getLastModified)
    val expired = req
      .flatMap(_.headers.get(`If-Modified-Since`)).forall(_.date.compareTo(lastmod) < 0)

    if (expired) {
      val mime    = MediaType.forExtension(url.getPath.split('.').last)
      val headers = Headers.apply(
        mime.fold(List[Header](`Last-Modified`(lastmod)))
          (`Content-Type`(_)::`Last-Modified`(lastmod)::Nil))

      Some(Response(
        headers = headers,
        body    = readInputStream[F](F.delay(url.openStream), DefaultBufferSize)
      ))
    } else Some(Response(NotModified))
  }

  def fromFile[F[_]: Sync](f: File, req: Option[Request[F]] = None): Option[Response[F]] =
    fromFile(f, DefaultBufferSize, req)

  def fromFile[F[_]: Sync](f: File, buffsize: Int, req: Option[Request[F]]): Option[Response[F]] = {
    fromFile(f, 0, f.length(), buffsize, req)
  }

  def fromFile[F[_]: Sync](f: File, start: Long, end: Long, buffsize: Int, req: Option[Request[F]]): Option[Response[F]] = {
    if (f.isFile) {

      require (start >= 0 && end >= start && buffsize > 0, s"start: $start, end: $end, buffsize: $buffsize")

      val lastModified = Instant.ofEpochMilli(f.lastModified())

      // See if we need to actually resend the file
      val notModified = for {
        r   <- req
        h   <- r.headers.get(`If-Modified-Since`)
        exp  = h.date.compareTo(lastModified) < 0
        _    = logger.trace(s"Expired: $exp. Request age: ${h.date}, Modified: $lastModified")
        nm   = Response[F](NotModified) if !exp
      } yield nm

      notModified orElse {

        val (body, contentLength) =
          if (f.length() < end) (empty.covary[F], 0L)
          else (fileToBody(f, start, end, buffsize), end - start)

        val contentType = {
          val name = f.getName

          name.lastIndexOf('.') match {
            case -1 => None
            case i  => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
          }
        }

        val hs = `Last-Modified`(lastModified) ::
          `Content-Length`.fromLong(contentLength).fold(_ => contentType.toList, _ :: contentType.toList)

        val r = Response(
          headers = Headers(hs),
          body = body,
          attributes = AttributeMap.empty.put(staticFileKey, f)
        )

        logger.trace(s"Static file generated response: $r")
        Some(r)
      }
    } else {
      None
    }
  }

  private def fileToBody[F[_]: Sync](f: File, start: Long, end: Long, buffsize: Int): EntityBody[F] = {
    // Based on fs2 handling of files
    def readAllFromFileHandle(chunkSize: Int, start: Long, end: Long)(h: FileHandle[F]): Pull[F, Byte, Unit] =
      _readAllFromFileHandle0(chunkSize, start, end)(h)

    def _readAllFromFileHandle0(chunkSize: Int, offset: Long, end: Long)(h: FileHandle[F]): Pull[F, Byte, Unit] =
      for {
        res  <- Pull.eval(h.read(math.min(chunkSize, (end - offset).toInt), offset))
        next <- res.filter(_.nonEmpty)
          .fold[Pull[F, Byte, Unit]](Pull.done)(o => Pull.output(o) >> _readAllFromFileHandle0(chunkSize, offset + o.size, end)(h))
      } yield next

    def readAll(path: Path, chunkSize: Int): Stream[F, Byte] =
      pulls
        .fromPath(path, List(StandardOpenOption.READ))
        .flatMap(h => readAllFromFileHandle(chunkSize, start, end)(h.resource))
        .stream

    readAll(f.toPath, DefaultBufferSize)
  }

  private[http4s] val staticFileKey = AttributeKey.http4s[File]("staticFile")
}
