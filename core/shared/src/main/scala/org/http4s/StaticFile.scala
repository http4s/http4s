/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Applicative
import cats.ApplicativeThrow
import cats.MonadError
import cats.MonadThrow
import cats.Semigroup
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.Sync
import cats.effect.SyncIO
import cats.syntax.all._
import fs2.Stream
import fs2.io._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.http4s.syntax.header._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen
import org.typelevel.vault._

import java.io._
import java.net.URL

object StaticFile {
  val DefaultBufferSize = 10240

  def fromString[F[_]: Files: MonadThrow: LoggerFactoryGen](
      url: String,
      req: Option[Request[F]] = None,
  ): OptionT[F, Response[F]] =
    fromPath(Path(url), req)

  def fromResource[F[_]: Sync](
      name: String,
      req: Option[Request[F]] = None,
      preferGzipped: Boolean = false,
      classloader: Option[ClassLoader] = None,
  ): OptionT[F, Response[F]] = {
    val loader = classloader.getOrElse(getClass.getClassLoader)

    val acceptEncodingHeader: Option[`Accept-Encoding`] =
      req.flatMap(_.headers.get[`Accept-Encoding`])
    val tryGzipped =
      preferGzipped && acceptEncodingHeader.exists { acceptEncoding =>
        acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
          ContentCoding.`x-gzip`
        )
      }
    val normalizedName = name.split("/").filter(_.nonEmpty).mkString("/")

    def getResource(name: String) =
      OptionT(Sync[F].blocking(Option(loader.getResource(name))))

    val gzUrl: OptionT[F, URL] =
      if (tryGzipped) getResource(normalizedName + ".gz") else OptionT.none

    gzUrl
      .flatMap { url =>
        fromURL(url, req).map {
          _.removeHeader[`Content-Type`]
            .putHeaders(
              `Content-Encoding`(ContentCoding.gzip),
              nameToContentType(normalizedName), // Guess content type from the name without ".gz"
            )
        }
      }
      .orElse(
        getResource(normalizedName)
          .flatMap(fromURL(_, req))
      )
  }

  def fromURL[F[_]](url: URL, req: Option[Request[F]] = None)(implicit
      F: Sync[F]
  ): OptionT[F, Response[F]] = {
    val fileUrl = url.getFile()
    val file = new File(fileUrl)
    OptionT.apply(F.defer {
      if (url.getProtocol === "file" && file.isDirectory())
        F.pure(none[Response[F]])
      else {
        val urlConn = url.openConnection
        val lastmod = HttpDate.fromEpochSecond(urlConn.getLastModified / 1000).toOption
        val ifModifiedSince: Option[`If-Modified-Since`] =
          req.flatMap(_.headers.get[`If-Modified-Since`])
        val expired = (ifModifiedSince, lastmod).mapN(_.date < _).getOrElse(true)

        if (expired) {
          val len = urlConn.getContentLengthLong
          val headers = Headers(
            lastmod.map(`Last-Modified`(_)),
            nameToContentType(url.getPath),
            if (len >= 0) `Content-Length`.unsafeFromLong(len)
            else `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
          )

          F.blocking(urlConn.getInputStream)
            .redeem(
              recover = {
                case _: FileNotFoundException => None
                case other => throw other
              },
              f = { inputStream =>
                val e = Entity.stream(readInputStream[F](F.pure(inputStream), DefaultBufferSize))
                Some(Response(headers = headers, entity = e))
              },
            )
        } else
          F.blocking(urlConn.getInputStream.close())
            .handleError(_ => ())
            .as(Some(Response(NotModified)))
      }
    })
  }

  def calculateETag[F[_]: Files: ApplicativeThrow]: Path => F[String] =
    f =>
      Files[F]
        .getBasicFileAttributes(f, followLinks = true)
        .map(attr =>
          if (attr.isRegularFile)
            s"${attr.lastModifiedTime.toMillis.toHexString}-${attr.size.toHexString}"
          else ""
        )

  def fromPath[F[_]: Files: MonadThrow: LoggerFactoryGen](
      f: Path,
      req: Option[Request[F]] = None,
  ): OptionT[F, Response[F]] =
    fromPath(f, DefaultBufferSize, req, calculateETag[F])

  def fromPath[F[_]: Files: MonadThrow: LoggerFactoryGen](
      f: Path,
      req: Option[Request[F]],
      etagCalculator: Path => F[String],
  ): OptionT[F, Response[F]] =
    fromPath(f, DefaultBufferSize, req, etagCalculator)

  def fromPath[F[_]: Files: MonadThrow: LoggerFactoryGen](
      f: Path,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: Path => F[String],
  ): OptionT[F, Response[F]] =
    OptionT
      .liftF(Files[F].getBasicFileAttributes(f, followLinks = true).map(_.size))
      .flatMap { size =>
        fromPath(f, 0, size, buffsize, req, etagCalculator)
      }
      .recoverWith { case _: fs2.io.file.NoSuchFileException =>
        OptionT.none
      }

  def fromPath[F[_]: Files: LoggerFactoryGen](
      f: Path,
      start: Long,
      end: Long,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: Path => F[String],
  )(implicit
      F: MonadError[F, Throwable]
  ): OptionT[F, Response[F]] = {
    implicit val logger: Logger[F] = LoggerFactory.getLogger[F]

    OptionT(for {
      etagCalc <- etagCalculator(f).map(et => ETag(et))
      res <- Files[F].isRegularFile(f).flatMap[Option[Response[F]]] { isFile =>
        if (isFile) {
          if (start >= 0 && end >= start && buffsize > 0) {
            Files[F]
              .getBasicFileAttributes(f, followLinks = true)
              .flatMap { attr =>
                val lastModified =
                  HttpDate.fromEpochSecond(attr.lastModifiedTime.toSeconds).toOption

                OptionT(notModified(req, etagCalc, lastModified)).orElseF {
                  val (body, contentLength) =
                    if (attr.size < end) (Stream.empty, 0L)
                    else (fileToBody[F](f, start, end), end - start)

                  val contentType = nameToContentType(f.fileName.toString)
                  val hs =
                    Headers(
                      lastModified.map(`Last-Modified`(_)),
                      `Content-Length`.fromLong(contentLength).toOption,
                      contentType,
                      etagCalc,
                    )

                  val r = Response(
                    headers = hs,
                    entity = Entity.stream(body),
                    attributes = Vault.empty.insert(staticPathKey, f),
                  )

                  logger
                    .trace(s"Static file generated response: $r")
                    .as(r.some)
                }.value
              }
          } else {
            F.raiseError[Option[Response[F]]](
              new IllegalArgumentException(
                s"requirement failed: start: $start, end: $end, buffsize: $buffsize"
              )
            )
          }

        } else {
          F.pure(none[Response[F]])
        }

      }
    } yield res)
  }

  private def notModified[F[_]: Applicative: Logger](
      req: Option[Request[F]],
      etagCalc: ETag,
      lastModified: Option[HttpDate],
  ): F[Option[Response[F]]] = {
    implicit val conjunction: Semigroup[Boolean] = new Semigroup[Boolean] {
      def combine(x: Boolean, y: Boolean): Boolean = x && y
    }

    List(etagMatch(req, etagCalc), notModifiedSince(req, lastModified)).sequence.map(
      _.combineAll
        .filter(identity)
        .map(_ => Response[F](NotModified))
    )
  }

  private def etagMatch[F[_]: Applicative: Logger](req: Option[Request[F]], etagCalc: ETag) =
    (for {
      r <- req
      etagHeader <- r.headers.get[`If-None-Match`]
      etagMatch = etagHeader.tags.exists(_.exists(_ == etagCalc.tag))
      log = Logger[F].trace(
        s"Matches `If-None-Match`: $etagMatch Previous ETag: ${etagHeader.value}, New ETag: $etagCalc"
      )
    } yield log.as(etagMatch)).sequence

  private def notModifiedSince[F[_]: Applicative: Logger](
      req: Option[Request[F]],
      lastModified: Option[HttpDate],
  ) =
    (for {
      r <- req
      h <- r.headers.get[`If-Modified-Since`]
      lm <- lastModified
      notModified = h.date >= lm
      log = Logger[F].trace(
        s"Matches `If-Modified-Since`: $notModified. Request age: ${h.date}, Modified: $lm"
      )
    } yield log.as(notModified)).sequence

  private def fileToBody[F[_]: Files](f: Path, start: Long, end: Long): EntityBody[F] =
    Files[F].readRange(f, DefaultBufferSize, start, end)

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

  private[http4s] val staticPathKey = Key.newKey[SyncIO, Path].unsafeRunSync()

}
