/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import java.nio.file.Paths
import org.http4s.server.middleware.TranslateUri
import org.log4s.getLogger
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

/** [[org.http4s.server.staticcontent.ResourceServiceBuilder]] builder
  *
  * @param basePath prefix of the path files will be served from
  * @param blocker execution context to use when collecting content
  * @param pathPrefix prefix of the Uri that content will be served from
  * @param bufferSize size hint of internal buffers to use when serving resources
  * @param cacheStrategy strategy to use for caching purposes.
  * @param preferGzipped whether to serve pre-gzipped files (with extension ".gz") if they exist
  * @param classLoader optional classloader for extracting the resources
  */
class ResourceServiceBuilder[F[_]] private (
    basePath: String,
    blocker: Blocker,
    pathPrefix: String,
    bufferSize: Int,
    cacheStrategy: CacheStrategy[F],
    preferGzipped: Boolean,
    classLoader: Option[ClassLoader]) {
  private[this] val logger = getLogger

  private def copy(
      basePath: String = basePath,
      blocker: Blocker = blocker,
      pathPrefix: String = pathPrefix,
      bufferSize: Int = bufferSize,
      cacheStrategy: CacheStrategy[F] = cacheStrategy,
      preferGzipped: Boolean = preferGzipped,
      classLoader: Option[ClassLoader] = classLoader): ResourceServiceBuilder[F] =
    new ResourceServiceBuilder[F](
      basePath,
      blocker,
      pathPrefix,
      bufferSize,
      cacheStrategy,
      preferGzipped,
      classLoader)

  def withBasePath(basePath: String): ResourceServiceBuilder[F] = copy(basePath = basePath)

  def withBlocker(blocker: Blocker): ResourceServiceBuilder[F] = copy(blocker = blocker)

  def withPathPrefix(pathPrefix: String): ResourceServiceBuilder[F] =
    copy(pathPrefix = pathPrefix)

  def withCacheStrategy(cacheStrategy: CacheStrategy[F]): ResourceServiceBuilder[F] =
    copy(cacheStrategy = cacheStrategy)

  def withPreferGzipped(preferGzipped: Boolean): ResourceServiceBuilder[F] =
    copy(preferGzipped = preferGzipped)

  def withClassLoader(classLoader: Option[ClassLoader]): ResourceServiceBuilder[F] =
    copy(classLoader = classLoader)

  def withBufferSize(bufferSize: Int): ResourceServiceBuilder[F] = copy(bufferSize = bufferSize)

  def toRoutes(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] = {
    val basePath = if (this.basePath.isEmpty) "/" else this.basePath
    object BadTraversal extends Exception with NoStackTrace

    Try(Paths.get(basePath)) match {
      case Success(rootPath) =>
        TranslateUri(pathPrefix)(Kleisli {
          case request if request.pathInfo.nonEmpty =>
            val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
            OptionT
              .liftF(F.catchNonFatal {
                segments.foldLeft(rootPath) {
                  case (_, "" | "." | "..") => throw BadTraversal
                  case (path, segment) =>
                    path.resolve(segment)
                }
              })
              .collect {
                case path if path.startsWith(rootPath) => path
              }
              .flatMap { path =>
                StaticFile.fromResource(
                  path.toString,
                  blocker,
                  Some(request),
                  preferGzipped = preferGzipped,
                  classLoader
                )
              }
              .semiflatMap(cacheStrategy.cache(request.pathInfo, _))
              .recoverWith {
                case BadTraversal => OptionT.some(Response(Status.BadRequest))
              }
          case _ => OptionT.none
        })

      case Failure(e) =>
        logger.error(e)(
          s"Could not get root path from ResourceService config: basePath = $basePath, pathPrefix = $pathPrefix. All requests will fail.")
        Kleisli(_ => OptionT.pure(Response(Status.InternalServerError)))
    }
  }
}

object ResourceServiceBuilder {
  def apply[F[_]](basePath: String, blocker: Blocker): ResourceServiceBuilder[F] =
    new ResourceServiceBuilder[F](
      basePath = basePath,
      blocker = blocker,
      pathPrefix = "",
      bufferSize = 50 * 1024,
      cacheStrategy = NoopCacheStrategy[F],
      preferGzipped = false,
      classLoader = None)
}

object ResourceService {
  private[this] val logger = getLogger

  /** [[org.http4s.server.staticcontent.ResourceService]] configuration
    *
    * @param basePath prefix of the path files will be served from
    * @param blocker execution context to use when collecting content
    * @param pathPrefix prefix of the Uri that content will be served from
    * @param bufferSize size hint of internal buffers to use when serving resources
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    * @param preferGzipped whether to serve pre-gzipped files (with extension ".gz") if they exist
    */
  final case class Config[F[_]](
      basePath: String,
      blocker: Blocker,
      pathPrefix: String = "",
      bufferSize: Int = 50 * 1024,
      cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
      preferGzipped: Boolean = false)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  @deprecated("use ResourceServiceBuilder", "1.0.0-M1")
  private[staticcontent] def apply[F[_]](
      config: Config[F])(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] = {
    val basePath = if (config.basePath.isEmpty) "/" else config.basePath
    object BadTraversal extends Exception with NoStackTrace

    Try(Paths.get(basePath)) match {
      case Success(rootPath) =>
        TranslateUri(config.pathPrefix)(Kleisli {
          case request if request.pathInfo.nonEmpty =>
            val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
            OptionT
              .liftF(F.catchNonFatal {
                segments.foldLeft(rootPath) {
                  case (_, "" | "." | "..") => throw BadTraversal
                  case (path, segment) =>
                    path.resolve(segment)
                }
              })
              .collect {
                case path if path.startsWith(rootPath) => path
              }
              .flatMap { path =>
                StaticFile.fromResource(
                  path.toString,
                  config.blocker,
                  Some(request),
                  preferGzipped = config.preferGzipped
                )
              }
              .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
              .recoverWith {
                case BadTraversal => OptionT.some(Response(Status.BadRequest))
              }
          case _ => OptionT.none
        })

      case Failure(e) =>
        logger.error(e)(
          s"Could not get root path from ResourceService config: basePath = ${config.basePath}, pathPrefix = ${config.pathPrefix}. All requests will fail.")
        Kleisli(_ => OptionT.pure(Response(Status.InternalServerError)))
    }
  }
}
