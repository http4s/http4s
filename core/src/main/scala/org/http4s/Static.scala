package org.http4s

import cats.implicits.{catsSyntaxEither => _, _}
import cats.data.OptionT
import cats.effect.{ContextShift, Sync}
import fs2.Stream.empty
import fs2.io.file.readRange
import java.io.File
import org.http4s.StaticFile._
import org.http4s.headers._
import org.http4s.Status.NotModified
import org.log4s.getLogger
import scala.concurrent.ExecutionContext


trait Static[F[_]] {
  def fromFile(f: File,
               blockingExecutionContext: ExecutionContext,
               start: Long = 0L,
               end: Option[Long],
               buffsize: Int = 8192,
               req: Option[Request[F]] = None,
               etagCalculator: File => F[String]): OptionT[F, Response[F]]
}

object Static {
  private[this] val logger = getLogger


  def calcETag[F[_]: Sync]: File => F[String] =
    f =>
      Sync[F].delay(
        if (f.isFile) s"${f.lastModified().toHexString}-${f.length().toHexString}" else "")

  private def fileToBody[F[_]: Sync: ContextShift](
    f: File,
    start: Long,
    end: Long,
    blockingExecutionContext: ExecutionContext): EntityBody[F] =
    readRange[F](f.toPath, blockingExecutionContext, DefaultBufferSize, start, end)

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

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

  def apply[F[_]](implicit F: Sync[F], cs: ContextShift[F]) = new Static[F] {
    def fromFile(f: File,
                 blockingExecutionContext: ExecutionContext,
                 start: Long = 0L,
                 end: Option[Long] = None,
                 buffsize: Int = 8192,
                 req: Option[Request[F]] = None,
                 etagCalculator: File => F[String] = calcETag[F]): OptionT[F, Response[F]] =
      OptionT(for {
        fileEndSize <- end.fold(F.delay(f.length()))(_.pure[F])
        etagCalc <- etagCalculator(f).map(et => ETag(et))
        res <- F.delay {
          if (f.isFile) {
            require(
              start >= 0 && fileEndSize >= start && buffsize > 0,
              s"start: $start, end: $fileEndSize, buffsize: $buffsize")

            val lastModified = HttpDate.fromEpochSecond(f.lastModified / 1000).toOption

            // See if we need to actually resend the file
            val notModified: Option[Response[F]] = ifModifiedSince(req, lastModified)

            // Check ETag
            val etagModified: Option[Response[F]] = ifETagModified(req, etagCalc)

            notModified.orElse(etagModified).orElse {
              val (body, contentLength) =
                if (f.length() < fileEndSize) (empty.covary[F], 0L)
                else (fileToBody[F](f, start, fileEndSize, blockingExecutionContext), fileEndSize - start)

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
  }
}
