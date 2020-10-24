/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.{MonadError, Semigroup}
import cats.data.OptionT
import cats.effect.{Sync, SyncIO}
import cats.implicits._
import fs2.Stream
import fs2.io._
import fs2.io.file.Files
import io.chrisdavenport.vault._
import java.io._
import java.nio.file.Path
import java.net.URL
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.log4s.getLogger

object StaticFile {
  private[this] val logger = getLogger

  val DefaultBufferSize = 10240

  def fromString[F[_]: Files: MonadError[*[_], Throwable]](
      url: String,
      req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromFile(new File(url), req)

  def fromResource[F[_]: Sync](
      name: String,
      req: Option[Request[F]] = None,
      preferGzipped: Boolean = false,
      classloader: Option[ClassLoader] = None): OptionT[F, Response[F]] = {
    val loader = classloader.getOrElse(getClass.getClassLoader)

    val tryGzipped =
      preferGzipped && req.flatMap(_.headers.get(`Accept-Encoding`)).exists { acceptEncoding =>
        acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
          ContentCoding.`x-gzip`)
      }
    val normalizedName = name.split("/").filter(_.nonEmpty).mkString("/")

    def getResource(name: String) =
      OptionT(Sync[F].delay(Option(loader.getResource(name))))

    val gzUrl: OptionT[F, URL] =
      if (tryGzipped) getResource(normalizedName + ".gz") else OptionT.none

    gzUrl
      .flatMap { url =>
        // Guess content type from the name without ".gz"
        val contentType = nameToContentType(normalizedName)
        val headers = `Content-Encoding`(ContentCoding.gzip) :: contentType.toList

        fromURL(url, req).map(_.removeHeader(`Content-Type`).putHeaders(headers: _*))
      }
      .orElse(getResource(normalizedName)
        .flatMap(fromURL(_, req)))
  }

  def fromURL[F[_]](url: URL, req: Option[Request[F]] = None)(implicit
      F: Sync[F]): OptionT[F, Response[F]] = {
    val fileUrl = url.getFile()
    val file = new File(fileUrl)
    OptionT.apply(F.defer {
      if (file.isDirectory())
        F.pure(none[Response[F]])
      else {
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
          F.blocking(urlConn.getInputStream)
            .redeem(
              recover = {
                case _: FileNotFoundException => None
                case other => throw other
              },
              f = { inputStream =>
                Some(
                  Response(
                    headers = headers,
                    body = readInputStream[F](F.pure(inputStream), DefaultBufferSize)
                  ))
              }
            )
        } else
          F.blocking(urlConn.getInputStream.close())
            .handleError(_ => ())
            .as(Some(Response(NotModified)))
      }
    })
  }

  // Placeholder for Files[F].isFile, which is yet to be merged on fs2
  def isFile[F[_]](path: Path)(implicit files: Files[F]): F[Boolean] = ???

  def calcETag[F[_]: Files: MonadError[*[_], Throwable]]: File => F[String] =
    f =>
      isFile(f.toPath()).map(isFile =>
        if (isFile) s"${f.lastModified().toHexString}-${f.length().toHexString}" else "")

  def fromFile[F[_]: Files: MonadError[*[_], Throwable]](
      f: File,
      req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromFile(f, DefaultBufferSize, req, calcETag[F])

  def fromFile[F[_]: Files: MonadError[*[_], Throwable]](
      f: File,
      req: Option[Request[F]],
      etagCalculator: File => F[String]): OptionT[F, Response[F]] =
    fromFile(f, DefaultBufferSize, req, etagCalculator)

  def fromFile[F[_]: Files: MonadError[*[_], Throwable]](
      f: File,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: File => F[String]): OptionT[F, Response[F]] =
    fromFile(f, 0, f.length(), buffsize, req, etagCalculator)

  def fromFile[F[_]: Files](
      f: File,
      start: Long,
      end: Long,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: File => F[String]
  )(implicit
      F: MonadError[F, Throwable]
  ): OptionT[F, Response[F]] =
    OptionT(for {
      etagCalc <- etagCalculator(f).map(et => ETag(et))
      res <- isFile(f.toPath()).flatMap[Option[Response[F]]] { isFile =>
        if (isFile) {

          if (start >= 0 && end >= start && buffsize > 0) {
            F.raiseError[Option[Response[F]]](
              new IllegalArgumentException(
                s"requirement failed: start: $start, end: $end, buffsize: $buffsize"))
          } else {

            val lastModified = HttpDate.fromEpochSecond(f.lastModified / 1000).toOption

            F.pure(notModified(req, etagCalc, lastModified).orElse {
              val (body, contentLength) =
                if (f.length() < end) (Stream.empty.covary[F], 0L)
                else (fileToBody[F](f, start, end), end - start)

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
              r.some
            })
          }

        } else {
          F.pure(none[Response[F]])
        }

      }
    } yield res)

  private def notModified[F[_]](
      req: Option[Request[F]],
      etagCalc: ETag,
      lastModified: Option[HttpDate]): Option[Response[F]] = {
    implicit val conjunction = new Semigroup[Boolean] {
      def combine(x: Boolean, y: Boolean): Boolean = x && y
    }

    List(etagMatch(req, etagCalc), notModifiedSince(req, lastModified)).combineAll
      .filter(identity)
      .map(_ => Response[F](NotModified))
  }

  private def etagMatch[F[_]](req: Option[Request[F]], etagCalc: ETag) =
    for {
      r <- req
      etagHeader <- r.headers.get(`If-None-Match`)
      etagMatch = etagHeader.tags.exists(_.exists(_ == etagCalc.tag))
      _ = logger.trace(
        s"Matches `If-None-Match`: $etagMatch Previous ETag: ${etagHeader.value}, New ETag: $etagCalc")
    } yield etagMatch

  private def notModifiedSince[F[_]](req: Option[Request[F]], lastModified: Option[HttpDate]) =
    for {
      r <- req
      h <- r.headers.get(`If-Modified-Since`)
      lm <- lastModified
      notModified = h.date >= lm
      _ = logger.trace(
        s"Matches `If-Modified-Since`: $notModified. Request age: ${h.date}, Modified: $lm")
    } yield notModified

  private def fileToBody[F[_]: Files](f: File, start: Long, end: Long): EntityBody[F] =
    Files[F].readRange(f.toPath, DefaultBufferSize, start, end)

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

  private[http4s] val staticFileKey =
    Key.newKey[SyncIO, File].unsafeRunSync()
}
