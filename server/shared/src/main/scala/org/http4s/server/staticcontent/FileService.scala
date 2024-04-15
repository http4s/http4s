/*
 * Copyright 2014 http4s.org
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
package server
package staticcontent

import cats.MonadThrow
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.io.file.Files
import fs2.io.file.NoSuchFileException
import fs2.io.file.Path
import org.http4s.headers.Range.SubRange
import org.http4s.headers._
import org.http4s.server.middleware.TranslateUri
import org.typelevel.ci._

import java.io.File
import scala.annotation.nowarn
import scala.util.control.NoStackTrace

object FileService {
  private[this] val logger = Platform.loggerFactory.getLogger

  @deprecated("use FileService.Fs2PathCollector", "0.23.8")
  type PathCollector[F[_]] = (File, Config[F], Request[F]) => OptionT[F, Response[F]]

  type Fs2PathCollector[F[_]] = (Path, Config[F], Request[F]) => OptionT[F, Response[F]]

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param fs2PathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param pathPrefix prefix of Uri from which content will be served
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    * @param bufferSize buffer size to use for internal read buffers
    */
  final class Config[F[_]](
      val systemPath: String,
      val fs2PathCollector: Fs2PathCollector[F],
      val pathPrefix: String,
      val cacheStrategy: CacheStrategy[F],
      val bufferSize: Int,
  ) extends Product
      with Serializable
      with Equals {

    /** For binary compatibility.
      * @param systemPath path prefix to the folder from which content will be served
      * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
      * @param pathPrefix prefix of Uri from which content will be served
      * @param bufferSize buffer size to use for internal read buffers
      * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
      */
    @deprecated("use the constructor with fs2PathCollector", "0.23.8")
    def this(
        systemPath: String,
        pathCollector: PathCollector[F],
        pathPrefix: String,
        bufferSize: Int,
        cacheStrategy: CacheStrategy[F],
    ) =
      this(
        systemPath,
        (path: Path, config: Config[F], request: Request[F]) =>
          pathCollector(
            path.toNioPath.toFile,
            config,
            request,
          ),
        pathPrefix,
        cacheStrategy,
        bufferSize,
      )

    /** For binary compatibility.
      * @return an instance of PathCollector[F] created (converted) from fs2PathCollector
      */
    @deprecated("use fs2PathCollector", "0.23.8")
    def pathCollector: PathCollector[F] = (file, config, request) =>
      fs2PathCollector(
        Path.fromNioPath(file.toPath()),
        config,
        request,
      )

    @deprecated(
      "Config is no longer a case class. The copy method is provided for binary compatibility.",
      "0.23.8",
    )
    def copy(
        systemPath: String = this.systemPath,
        @nowarn pathCollector: PathCollector[F] = this.pathCollector,
        pathPrefix: String = this.pathPrefix,
        bufferSize: Int = this.bufferSize,
        cacheStrategy: CacheStrategy[F] = this.cacheStrategy,
    ): Config[F] = new Config[F](
      systemPath,
      fs2PathCollector,
      pathPrefix,
      cacheStrategy,
      bufferSize,
    )

    @deprecated(
      "Config is no longer a case class. The productArity method is provided for binary compatibility.",
      "0.23.8",
    )
    override def productArity: Int = 5

    @deprecated(
      "Config is no longer a case class. The productElement method is provided for binary compatibility.",
      "0.23.8",
    )
    override def productElement(n: Int): Any = n match {
      case 0 => systemPath
      case 1 => if (Platform.isJvm) pathCollector else fs2PathCollector
      case 2 => pathPrefix
      case 3 => bufferSize
      case 4 => cacheStrategy
    }

    @deprecated(
      "Config is no longer a case class. The _1 method is provided for binary compatibility.",
      "0.23.8",
    )
    def _1: String = systemPath
    @deprecated(
      "Config is no longer a case class. The _2 method is provided for binary compatibility.",
      "0.23.8",
    )
    def _2: Function3[_, _, _, _] =
      if (Platform.isJvm) pathCollector else fs2PathCollector
    @deprecated(
      "Config is no longer a case class. The _3 method is provided for binary compatibility.",
      "0.23.8",
    )
    def _3: String = pathPrefix
    @deprecated(
      "Config is no longer a case class. The _4 method is provided for binary compatibility.",
      "0.23.8",
    )
    def _4: Int = bufferSize
    @deprecated(
      "Config is no longer a case class. The _5 method is provided for binary compatibility.",
      "0.23.8",
    )
    def _5: CacheStrategy[F] = cacheStrategy

    override def canEqual(that: Any): Boolean = that match {
      case _: Config[_] => true
      case _ => false
    }

    override def equals(other: Any): Boolean = other match {
      case that: Config[_] =>
        systemPath == that.systemPath &&
        fs2PathCollector == that.fs2PathCollector &&
        pathPrefix == that.pathPrefix &&
        cacheStrategy == that.cacheStrategy &&
        bufferSize == that.bufferSize
      case _ => false
    }

    override def hashCode(): Int = {
      val state = Seq[Any](systemPath, fs2PathCollector, pathPrefix, bufferSize, cacheStrategy)
      state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
    }

    override def toString: String =
      s"Config($systemPath, $fs2PathCollector, $pathPrefix, $bufferSize, $cacheStrategy)"

  }

  object Config extends FileServiceConfigCompanionCompat {

    /** Creates an instance of [[org.http4s.server.staticcontent.FileService]] configuration.
      *
      * @param systemPath path prefix to the folder from which content will be served
      * @param fs2PathCollector function that performs the work of collecting the file or rendering the directory into a response.
      * @param pathPrefix prefix of Uri from which content will be served
      * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
      * @param bufferSize buffer size to use for internal read buffers
      */
    def apply[F[_]](
        systemPath: String,
        fs2PathCollector: Fs2PathCollector[F],
        pathPrefix: String,
        cacheStrategy: CacheStrategy[F],
        bufferSize: Int,
    ): Config[F] =
      new Config[F](systemPath, fs2PathCollector, pathPrefix, cacheStrategy, bufferSize)

    /** Creates an instance of [[org.http4s.server.staticcontent.FileService]] configuration.
      * A constructor that accepts PathCollector[F] for binary compatibility.
      *
      * @param systemPath path prefix to the folder from which content will be served
      * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
      * @param pathPrefix prefix of Uri from which content will be served
      * @param bufferSize buffer size to use for internal read buffers
      * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
      */
    @deprecated(
      "use FileService.Config(systemPath: String, fs2PathCollector: Fs2PathCollector[F], ...)",
      "0.23.8",
    )
    def apply[F[_]](
        systemPath: String,
        pathCollector: PathCollector[F],
        pathPrefix: String,
        bufferSize: Int,
        cacheStrategy: CacheStrategy[F],
    ): Config[F] = new Config[F](
      systemPath,
      pathCollector,
      pathPrefix,
      bufferSize,
      cacheStrategy,
    )

    def apply[F[_]: Async: Files](
        systemPath: String,
        pathPrefix: String = "",
        bufferSize: Int = 50 * 1024,
        cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
    ): Config[F] = {
      val pathCollector: Fs2PathCollector[F] = (f, c, r) => filesOnly(f, c, r)
      Config(systemPath, pathCollector, pathPrefix, cacheStrategy, bufferSize)
    }

    @deprecated("Use overload with Files constraint", "0.23.19")
    def apply[F[_]](
        systemPath: String,
        pathPrefix: String,
        bufferSize: Int,
        cacheStrategy: CacheStrategy[F],
        F: Async[F],
    ): Config[F] = apply(systemPath, pathPrefix, bufferSize, cacheStrategy)(F, Files.forAsync(F))
  }

  @deprecated("Use overload with Files constraint", "0.23.19")
  private[staticcontent] def apply[F[_]](
      config: Config[F],
      F: Async[F],
  ): HttpRoutes[F] = apply(config)(Files.forAsync(F), F)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  private[staticcontent] def apply[F[_]: Files](
      config: Config[F]
  )(implicit F: Async[F]): HttpRoutes[F] = {
    object BadTraversal extends Exception with NoStackTrace
    def withPath(rootPath: Path)(request: Request[F]): OptionT[F, Response[F]] = {
      val resolvePath: F[Path] =
        if (request.pathInfo.isEmpty) F.pure(rootPath)
        else {
          val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
          F.catchNonFatal {
            segments.foldLeft(rootPath) {
              case (_, "" | "." | "..") => throw BadTraversal
              case (path, segment) => path.resolve(segment)
            }
          }
        }

      val matchingPath: F[Option[Path]] =
        for {
          path <- resolvePath
          existsPath <- Files[F].exists(path, false)
        } yield
          if (existsPath && path.startsWith(rootPath))
            Some(path.absolute.normalize)
          else None

      OptionT(matchingPath)
        .flatMap(path => config.fs2PathCollector(path, config, request))
        .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
        .recoverWith { case BadTraversal => OptionT.some(Response(Status.BadRequest)) }
    }

    val readPath: F[Path] = Files[F].realPath(Path(config.systemPath))
    val inner: F[HttpRoutes[F]] = readPath.attempt.flatMap {
      case Right(rootPath) =>
        TranslateUri(config.pathPrefix)(Kleisli(withPath(rootPath))).pure

      case Left(_: NoSuchFileException) =>
        logger
          .error(
            s"Could not find root path from FileService config: systemPath = ${config.systemPath}, pathPrefix = ${config.pathPrefix}. All requests will return none."
          )
          .to[F]
          .as(HttpRoutes.empty)

      case Left(e) =>
        logger
          .error(e)(
            s"Could not resolve root path from FileService config: systemPath = ${config.systemPath}, pathPrefix = ${config.pathPrefix}. All requests will fail with a 500."
          )
          .to[F]
          .as(HttpRoutes.pure(Response(Status.InternalServerError)))
    }

    Kleisli((_: Any) => OptionT.liftF(inner)).flatten
  }

  private def filesOnly[F[_]: Files](path: Path, config: Config[F], req: Request[F])(implicit
      F: MonadThrow[F]
  ): OptionT[F, Response[F]] =
    OptionT(Files[F].getBasicFileAttributes(path).flatMap { attr =>
      if (attr.isDirectory)
        StaticFile
          .fromPath(path / "index.html", Some(req))
          .value
      else if (!attr.isRegularFile) F.pure(None)
      else
        OptionT(getPartialContentFile(path, config, req))
          .orElse(
            StaticFile
              .fromPath(path, config.bufferSize, Some(req), StaticFile.calculateETag)
              .map(_.putHeaders(AcceptRangeHeader))
          )
          .value
    })

  private def validRange(start: Long, end: Option[Long], fileLength: Long): Boolean =
    start < fileLength && (end match {
      case Some(end) => start >= 0 && start <= end
      case None => start >= 0 || fileLength + start - 1 >= 0
    })

  // Attempt to find a Range header and collect only the subrange of content requested
  private def getPartialContentFile[F[_]: Files](file: Path, config: Config[F], req: Request[F])(
      implicit F: MonadThrow[F]
  ): F[Option[Response[F]]] =
    Files[F].getBasicFileAttributes(file).flatMap { attr =>
      def nope: F[Option[Response[F]]] =
        Some(
          Response[F](
            status = Status.RangeNotSatisfiable,
            headers = Headers
              .apply(
                AcceptRangeHeader,
                `Content-Range`(SubRange(0, attr.size - 1), Some(attr.size)),
              ),
          )
        ).pure[F].widen

      req.headers.get[Range] match {
        case Some(Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil))) =>
          if (validRange(s, e, attr.size)) {
            val size = attr.size
            val start = if (s >= 0) s else math.max(0, size + s)
            val end = math.min(size - 1, e.getOrElse(size - 1)) // end is inclusive

            StaticFile
              .fromPath(
                file,
                start,
                end + 1,
                config.bufferSize,
                Some(req),
                StaticFile.calculateETag,
              )
              .map { resp =>
                val hs = resp.headers
                  .put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
                resp.copy(status = Status.PartialContent, headers = hs)
              }
              .value
          } else nope
        case _ =>
          req.headers.get(ci"Range") match {
            case Some(_) =>
              // It exists, but it didn't parse
              nope
            case None =>
              F.pure(None)
          }
      }
    }
}
