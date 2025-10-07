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

import cats.ApplicativeThrow
import cats.Functor
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
import org.typelevel.vault._

import java.io._
import java.net.URL

object StaticFile {
  private[this] val logger = Platform.loggerFactory.getLogger

  val DefaultBufferSize = 10240

  def fromString[F[_]: Files: MonadThrow](
      url: String,
      req: Option[Request[F]] = None,
  ): OptionT[F, Response[F]] =
    fromPath(Path(url), req)

  def fromResource[F[_]: Sync](
      name: String,
      req: Option[Request[F]] = None,
      preferGzipped: Boolean = false,
      classloader: Option[ClassLoader] = None,
  ): OptionT[F, Response[F]] =
    fromResource(name, req, preferGzipped, classloader, calcETagURL)

  def fromResource[F[_]: Sync](
      name: String,
      req: Option[Request[F]],
      preferGzipped: Boolean,
      classloader: Option[ClassLoader],
      etagCalculator: URL => F[Option[ETag]],
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
        fromURL(url, req, etagCalculator).map {
          _.removeHeader[`Content-Type`]
            .putHeaders(
              `Content-Encoding`(ContentCoding.gzip),
              nameToContentType(normalizedName), // Guess content type from the name without ".gz"
            )
        }
      }
      .orElse(
        getResource(normalizedName)
          .flatMap(fromURL(_, req, etagCalculator))
      )
  }

  def fromURL[F[_]: Sync](url: URL, req: Option[Request[F]] = None): OptionT[F, Response[F]] =
    fromURL(url, req, calcETagURL[F])

  def fromURL[F[_]](url: URL, req: Option[Request[F]], etagCalculator: URL => F[Option[ETag]])(
      implicit F: Sync[F]
  ): OptionT[F, Response[F]] = {
    val fileUrl = url.getFile()
    val file = new File(fileUrl)
    OptionT.apply(F.defer {
      if (url.getProtocol === "file" && file.isDirectory())
        F.pure(none[Response[F]])
      else {
        F.blocking(url.openConnection).flatMap { urlConn =>
          (F.blocking(urlConn.getLastModified()), F.blocking(urlConn.getContentLengthLong())).tupled
            .flatMap { case (lastModified, contentLength) =>
              val lastmod = HttpDate.fromEpochSecond(lastModified / 1000).toOption
              val ifModifiedSince: Option[`If-Modified-Since`] =
                req.flatMap(_.headers.get[`If-Modified-Since`])
              val expired = (ifModifiedSince, lastmod).mapN(_.date < _).getOrElse(true)

              if (expired) {
                etagCalculator(url).flatMap { etag =>
                  val headers = Headers(
                    lastmod.map(`Last-Modified`(_)),
                    nameToContentType(url.getPath),
                    etag,
                    if (contentLength >= 0) `Content-Length`.unsafeFromLong(contentLength)
                    else `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
                  )
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
                            body = readInputStream[F](F.pure(inputStream), DefaultBufferSize),
                          )
                        )
                      },
                    )
                }
              } else
                F.blocking(urlConn.getInputStream.close())
                  .handleError(_ => ())
                  .as(Some(Response(NotModified)))
            }
        }
      }
    })
  }

  private def calcETagURL[F[_]](implicit F: Sync[F]): URL => F[Option[ETag]] = url =>
    for {
      urlConn <- F.blocking(url.openConnection)
      lastModified <- F.blocking(urlConn.getLastModified)
      contentLength <- F.blocking(urlConn.getContentLengthLong)
    } yield
      if (lastModified == 0 || contentLength == -1) None
      else Some(ETag(s"${lastModified.toHexString}-${contentLength.toHexString}"))

  @deprecated("Use calculateETag", "0.23.5")
  def calcETag[F[_]: Files: Functor]: File => F[String] =
    f =>
      Files[F]
        .isRegularFile(Path.fromNioPath(f.toPath()))
        .map(isFile =>
          if (isFile) s"${f.lastModified().toHexString}-${f.length().toHexString}" else ""
        )

  def calculateETag[F[_]: Files: ApplicativeThrow]: Path => F[String] =
    f =>
      Files[F]
        .getBasicFileAttributes(f, followLinks = true)
        .map(attr =>
          if (attr.isRegularFile)
            s"${attr.lastModifiedTime.toMillis.toHexString}-${attr.size.toHexString}"
          else ""
        )

  @deprecated("Use fromPath", "0.23.5")
  def fromFile[F[_]: Files: MonadThrow](
      f: File,
      req: Option[Request[F]] = None,
  ): OptionT[F, Response[F]] =
    fromPath(Path.fromNioPath(f.toPath()), DefaultBufferSize, req, calculateETag[F])

  def fromPath[F[_]: Files: MonadThrow](
      f: Path,
      req: Option[Request[F]] = None,
  ): OptionT[F, Response[F]] =
    fromPath(f, DefaultBufferSize, req, calculateETag[F])

  @deprecated("Use fromPath", "0.23.5")
  def fromFile[F[_]: Files: MonadThrow](
      f: File,
      req: Option[Request[F]],
      etagCalculator: File => F[String],
  ): OptionT[F, Response[F]] =
    fromPath(
      Path.fromNioPath(f.toPath()),
      DefaultBufferSize,
      req,
      etagCalculator.compose(_.toNioPath.toFile()),
    )

  def fromPath[F[_]: Files: MonadThrow](
      f: Path,
      req: Option[Request[F]],
      etagCalculator: Path => F[String],
  ): OptionT[F, Response[F]] =
    fromPath(f, DefaultBufferSize, req, etagCalculator)

  @deprecated("Use fromPath", "0.23.5")
  def fromFile[F[_]: Files: MonadThrow](
      f: File,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: File => F[String],
  ): OptionT[F, Response[F]] =
    fromPath(
      Path.fromNioPath(f.toPath()),
      0,
      f.length(),
      buffsize,
      req,
      etagCalculator.compose(_.toNioPath.toFile()),
    )

  def fromPath[F[_]: Files: MonadThrow](
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

  @deprecated("Use fromPath", "0.23.5")
  def fromFile[F[_]: Files](
      f: File,
      start: Long,
      end: Long,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: File => F[String],
  )(implicit
      F: MonadError[F, Throwable]
  ): OptionT[F, Response[F]] =
    fromPath(
      Path.fromNioPath(f.toPath()),
      start,
      end,
      buffsize,
      req,
      etagCalculator.compose(_.toNioPath.toFile()),
    )

  def fromPath[F[_]: Files](
      f: Path,
      start: Long,
      end: Long,
      buffsize: Int,
      req: Option[Request[F]],
      etagCalculator: Path => F[String],
  )(implicit
      F: MonadError[F, Throwable]
  ): OptionT[F, Response[F]] =
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

                F.pure(notModified(req, etagCalc, lastModified).orElse {
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
                    body = body,
                    attributes = Vault.empty.insert(staticPathKey, f),
                  )

                  logger.trace(s"Static file generated response: $r").unsafeRunSync()
                  r.some
                })
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

  private def notModified[F[_]](
      req: Option[Request[F]],
      etagCalc: ETag,
      lastModified: Option[HttpDate],
  ): Option[Response[F]] = {
    implicit val conjunction: Semigroup[Boolean] = new Semigroup[Boolean] {
      def combine(x: Boolean, y: Boolean): Boolean = x && y
    }

    List(etagMatch(req, etagCalc), notModifiedSince(req, lastModified)).combineAll
      .filter(identity)
      .map(_ => Response[F](NotModified))
  }

  private def etagMatch[F[_]](req: Option[Request[F]], etagCalc: ETag) =
    for {
      r <- req
      etagHeader <- r.headers.get[`If-None-Match`]
      etagMatch = etagHeader.tags.exists(_.exists(_ == etagCalc.tag))
      _ = logger.trace(
        s"Matches `If-None-Match`: $etagMatch Previous ETag: ${etagHeader.value}, New ETag: $etagCalc"
      )
    } yield etagMatch

  private def notModifiedSince[F[_]](req: Option[Request[F]], lastModified: Option[HttpDate]) =
    for {
      r <- req
      h <- r.headers.get[`If-Modified-Since`]
      lm <- lastModified
      notModified = h.date >= lm
      _ = logger.trace(
        s"Matches `If-Modified-Since`: $notModified. Request age: ${h.date}, Modified: $lm"
      )
    } yield notModified

  private def fileToBody[F[_]: Files](f: Path, start: Long, end: Long): EntityBody[F] =
    Files[F].readRange(f, DefaultBufferSize, start, end)

  private def nameToContentType(name: String): Option[`Content-Type`] =
    name.lastIndexOf('.') match {
      case -1 => None
      case i => MediaType.forExtension(name.substring(i + 1)).map(`Content-Type`(_))
    }

  private[http4s] val staticPathKey = Key.newKey[SyncIO, Path].unsafeRunSync()

  @deprecated("Use staticPathKey", since = "0.23.5")
  private[http4s] lazy val staticFileKey: Key[File] =
    staticPathKey.imap(_.toNioPath.toFile)(f => Path.fromNioPath(f.toPath))
}
