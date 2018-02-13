package org.http4s

import cats.data._
import cats.effect.Sync
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import fs2.Stream._
import fs2.io._
import fs2.io.file.{FileHandle, pulls}
import java.io._
import java.net.URL
import java.nio.file.{Path, StandardOpenOption}
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.log4s.getLogger

object StaticFile {
  private[this] val logger = getLogger

  val DefaultBufferSize = 10240

  def fromString[F[_]: Sync](url: String, req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromFile(new File(url), req)

  def fromResource[F[_]: Sync](
      name: String,
      req: Option[Request[F]] = None,
      preferGzipped: Boolean = false): OptionT[F, Response[F]] = {
    val tryGzipped = preferGzipped && req.flatMap(_.headers.get(`Accept-Encoding`)).exists {
      acceptEncoding =>
        acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
          ContentCoding.`x-gzip`)
    }

    val gzUrl: OptionT[F, URL] =
      if (tryGzipped) OptionT.fromOption(Option(getClass.getResource(name + ".gz")))
      else OptionT.none

    gzUrl
      .flatMap { url =>
        // Guess content type from the name without ".gz"
        val contentType = nameToContentType(name)
        val headers = `Content-Encoding`(ContentCoding.gzip) :: contentType.toList

        fromURL(url, req).map(_.removeHeader(`Content-Type`).putHeaders(headers: _*))
      }
      .orElse(OptionT.fromOption[F](Option(getClass.getResource(name))).flatMap(fromURL(_, req)))
  }

  def fromURL[F[_]](url: URL, req: Option[Request[F]] = None)(
      implicit F: Sync[F]): OptionT[F, Response[F]] =
    OptionT.liftF(F.delay {
      val urlConn = url.openConnection
      val lastmod = HttpDate.fromEpochSecond(urlConn.getLastModified / 1000).toOption
      val ifModifiedSince = req.flatMap(_.headers.get(`If-Modified-Since`))
      val expired = (ifModifiedSince, lastmod).mapN(_.date < _).getOrElse(true)

      if (expired) {
        val lastModHeader: List[Header] = lastmod.map(`Last-Modified`(_)).toList
        val contentType = nameToContentType(url.getPath).toList
        val len = urlConn.getContentLengthLong
        val lenHeader =
          if (len >= 0) `Content-Length`.unsafeFromLong(len)
          else `Transfer-Encoding`(TransferCoding.chunked)
        val headers = Headers(lenHeader :: lastModHeader ::: contentType)

        Response(
          headers = headers,
          body = readInputStream[F](F.delay(url.openStream), DefaultBufferSize)
        )
      } else {
        urlConn.getInputStream.close()
        Response(NotModified)
      }
    })

  def calcETag[F[_]: Sync]: File => F[String] =
    f =>
      Sync[F].delay(
        if (f.isFile) s"${f.lastModified().toHexString}-${f.length().toHexString}" else "")

  def fromFile[F[_]: Sync](f: File, req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromFile(f, DefaultBufferSize, req, calcETag[F])

  def fromFile[F[_]: Sync](
      f: File,
      req: Option[Request[F]],
      etagCalculator: File => F[String]): OptionT[F, Response[F]] =
    fromFile(f, DefaultBufferSize, req, etagCalculator)

  def fromFile[F[_]: Sync](
      f: File,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: File => F[String]): OptionT[F, Response[F]] =
    fromFile(f, 0, f.length(), buffsize, req, etagCalculator)

  def fromFile[F[_]](
      f: File,
      start: Long,
      end: Long,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: File => F[String])(implicit F: Sync[F]): OptionT[F, Response[F]] =
    OptionT(for {
      etagCalc <- etagCalculator(f).map(et => ETag(et))
      res <- F.delay {
        if (f.isFile) {
          require(
            start >= 0 && end >= start && buffsize > 0,
            s"start: $start, end: $end, buffsize: $buffsize")

          val lastModified = HttpDate.fromEpochSecond(f.lastModified / 1000).toOption

          // See if we need to actually resend the file
          val notModified: Option[Response[F]] = ifModifiedSince(req, lastModified)

          // Check ETag
          val etagModified: Option[Response[F]] = ifETagModified(req, etagCalc)

          notModified.orElse(etagModified).orElse {
            val (body, contentLength) =
              if (f.length() < end) (empty.covary[F], 0L)
              else (fileToBody[F](f, start, end), end - start)

            val contentType = nameToContentType(f.getName)
            val hs = lastModified.map(lm => `Last-Modified`(lm)).toList :::
              `Content-Length`.fromLong(contentLength).toList :::
              contentType.toList ::: List(etagCalc)

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
    } yield res)

  private def ifETagModified[F[_]](req: Option[Request[F]], etagCalc: ETag) =
    for {
      r <- req
      etagHeader <- r.headers.get(ETag)
      etagExp = etagHeader.value != etagCalc.value
      _ = logger.trace(
        s"Expired ETag: $etagExp Previous ETag: ${etagHeader.value}, New ETag: $etagCalc")
      nm = Response[F](NotModified) if !etagExp
    } yield nm

  private def ifModifiedSince[F[_]](req: Option[Request[F]], lastModified: Option[HttpDate]) =
    for {
      r <- req
      h <- r.headers.get(`If-Modified-Since`)
      lm <- lastModified
      exp = h.date.compareTo(lm) < 0
      _ = logger.trace(s"Expired: $exp. Request age: ${h.date}, Modified: $lm")
      nm = Response[F](NotModified) if !exp
    } yield nm

  private def fileToBody[F[_]: Sync](
      f: File,
      start: Long,
      end: Long
  ): EntityBody[F] = {
    // Based on fs2 handling of files
    def readAllFromFileHandle(chunkSize: Int, start: Long, end: Long)(
        h: FileHandle[F]): Pull[F, Byte, Unit] =
      _readAllFromFileHandle0(chunkSize, start, end)(h)

    def _readAllFromFileHandle0(chunkSize: Int, offset: Long, end: Long)(
        h: FileHandle[F]): Pull[F, Byte, Unit] = {
      val bytesLeft = end - offset
      if (bytesLeft <= 0L) Pull.done
      else {
        val bufferSize =
          if (bytesLeft > Int.MaxValue) chunkSize else math.min(chunkSize, bytesLeft.toInt)
        for {
          res <- Pull.eval(h.read(bufferSize, offset))
          next <- res
            .filter(_.nonEmpty)
            .fold[Pull[F, Byte, Unit]](Pull.done)(o =>
              Pull
                .output(o.toSegment) >> _readAllFromFileHandle0(chunkSize, offset + o.size, end)(h))
        } yield next
      }
    }

    def readAll(path: Path, chunkSize: Int): Stream[F, Byte] =
      pulls
        .fromPath[F](path, List(StandardOpenOption.READ))
        .flatMap(h => readAllFromFileHandle(chunkSize, start, end)(h.resource))
        .stream

    readAll(f.toPath, DefaultBufferSize)
  }

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

  private[http4s] val staticFileKey = AttributeKey[File]
}
