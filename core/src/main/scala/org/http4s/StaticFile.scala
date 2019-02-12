package org.http4s

import cats.data._
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2.Stream._
import fs2.io._
import fs2.io.file.readRange
import java.io._
import java.net.URL
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.log4s.getLogger
import scala.concurrent.ExecutionContext
import _root_.io.chrisdavenport.vault._

object StaticFile {
  private[this] val logger = getLogger

  val DefaultBufferSize = 10240

  def fromString[F[_]: Sync: ContextShift](
      url: String,
      blockingExecutionContext: ExecutionContext,
      req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromFile(new File(url), blockingExecutionContext, req)

  def fromResource[F[_]: Sync: ContextShift](
      name: String,
      blockingExecutionContext: ExecutionContext,
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

        fromURL(url, blockingExecutionContext, req).map(
          _.removeHeader(`Content-Type`).putHeaders(headers: _*))
      }
      .orElse(OptionT
        .fromOption[F](Option(getClass.getResource(name)))
        .flatMap(fromURL(_, blockingExecutionContext, req)))
  }

  def fromURL[F[_]](
      url: URL,
      blockingExecutionContext: ExecutionContext,
      req: Option[Request[F]] = None)(
      implicit F: Sync[F],
      cs: ContextShift[F]): OptionT[F, Response[F]] =
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
          body =
            readInputStream[F](F.delay(url.openStream), DefaultBufferSize, blockingExecutionContext)
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

  def fromFile[F[_]: Sync: ContextShift](
      f: File,
      blockingExecutionContext: ExecutionContext,
      req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromFile(f, DefaultBufferSize, blockingExecutionContext, req, calcETag[F])

  def fromFile[F[_]: Sync: ContextShift](
      f: File,
      blockingExecutionContext: ExecutionContext,
      req: Option[Request[F]],
      etagCalculator: File => F[String]): OptionT[F, Response[F]] =
    fromFile(f, DefaultBufferSize, blockingExecutionContext, req, etagCalculator)

  def fromFile[F[_]: Sync: ContextShift](
      f: File,
      buffsize: Int,
      blockingExecutionContext: ExecutionContext,
      req: Option[Request[F]],
      etagCalculator: File => F[String]): OptionT[F, Response[F]] =
    fromFile(f, 0, f.length(), buffsize, blockingExecutionContext, req, etagCalculator)

  def fromFile[F[_]](
      f: File,
      start: Long,
      end: Long,
      buffsize: Int,
      blockingExecutionContext: ExecutionContext,
      req: Option[Request[F]],
      etagCalculator: File => F[String])(
      implicit F: Sync[F],
      cs: ContextShift[F]): OptionT[F, Response[F]] =
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
              else (fileToBody[F](f, start, end, blockingExecutionContext), end - start)

            val contentType = nameToContentType(f.getName)
            val hs = lastModified.map(lm => `Last-Modified`(lm)).toList :::
              `Content-Length`.fromLong(contentLength).toList :::
              contentType.toList ::: List(etagCalc)

            val r = Response(
              headers = Headers(hs),
              body = body,
              attributes = Vault.empty.insert(staticFileKey, f)
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

  private def fileToBody[F[_]: Sync: ContextShift](
      f: File,
      start: Long,
      end: Long,
      blockingExecutionContext: ExecutionContext
  ): EntityBody[F] =
    readRange[F](f.toPath, blockingExecutionContext, DefaultBufferSize, start, end)

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

  private[http4s] val staticFileKey = Key.newKey[IO, File].unsafeRunSync
}
