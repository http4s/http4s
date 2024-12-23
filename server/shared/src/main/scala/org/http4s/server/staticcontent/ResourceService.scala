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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.all._
import org.http4s.server.middleware.TranslateUri
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen

import java.io.File
import java.nio.file.Paths
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NoStackTrace

/** [[org.http4s.server.staticcontent.ResourceServiceBuilder]] builder
  *
  * @param basePath prefix of the path files will be served from
  * @param pathPrefix prefix of the Uri that content will be served from
  * @param bufferSize size hint of internal buffers to use when serving resources
  * @param cacheStrategy strategy to use for caching purposes.
  * @param preferGzipped whether to serve pre-gzipped files (with extension ".gz") if they exist
  * @param classLoader optional classloader for extracting the resources
  */
class ResourceServiceBuilder[F[_]: LoggerFactoryGen] private (
    basePath: String,
    pathPrefix: String,
    bufferSize: Int,
    cacheStrategy: CacheStrategy[F],
    preferGzipped: Boolean,
    classLoader: Option[ClassLoader],
) {
  private[this] val logger = LoggerFactory.getLogger[F]

  private def copy(
      basePath: String = basePath,
      pathPrefix: String = pathPrefix,
      bufferSize: Int = bufferSize,
      cacheStrategy: CacheStrategy[F] = cacheStrategy,
      preferGzipped: Boolean = preferGzipped,
      classLoader: Option[ClassLoader] = classLoader,
  ): ResourceServiceBuilder[F] =
    new ResourceServiceBuilder[F](
      basePath,
      pathPrefix,
      bufferSize,
      cacheStrategy,
      preferGzipped,
      classLoader,
    )

  def withBasePath(basePath: String): ResourceServiceBuilder[F] = copy(basePath = basePath)
  def withPathPrefix(pathPrefix: String): ResourceServiceBuilder[F] =
    copy(pathPrefix = pathPrefix)

  def withCacheStrategy(cacheStrategy: CacheStrategy[F]): ResourceServiceBuilder[F] =
    copy(cacheStrategy = cacheStrategy)

  def withPreferGzipped(preferGzipped: Boolean): ResourceServiceBuilder[F] =
    copy(preferGzipped = preferGzipped)

  def withClassLoader(classLoader: Option[ClassLoader]): ResourceServiceBuilder[F] =
    copy(classLoader = classLoader)

  def withBufferSize(bufferSize: Int): ResourceServiceBuilder[F] = copy(bufferSize = bufferSize)

  def toRoutes(implicit F: Async[F]): F[HttpRoutes[F]] = {
    val basePath = if (this.basePath.isEmpty) "/" else this.basePath
    object BadTraversal extends Exception with NoStackTrace

    Try(Paths.get(basePath)) match {
      case Success(rootPath) =>
        TranslateUri(pathPrefix)(HttpRoutes[F] {
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
                  path.toString.replace(File.separatorChar, '/'),
                  Some(request),
                  preferGzipped = preferGzipped,
                  classLoader,
                )
              }
              .semiflatMap(cacheStrategy.cache(request.pathInfo, _))
              .recoverWith { case BadTraversal =>
                OptionT.some(Response(Status.BadRequest))
              }
          case _ => OptionT.none
        }).pure[F]

      case Failure(e) =>
        logger
          .error(e)(
            s"Could not get root path from ResourceService config: basePath = $basePath, pathPrefix = $pathPrefix. All requests will fail."
          )
          .as(Kleisli(_ => OptionT.pure(Response(Status.InternalServerError))))

    }
  }
}

object ResourceServiceBuilder {
  def apply[F[_]: LoggerFactoryGen](basePath: String): ResourceServiceBuilder[F] =
    new ResourceServiceBuilder[F](
      basePath = basePath,
      pathPrefix = "",
      bufferSize = 50 * 1024,
      cacheStrategy = NoopCacheStrategy[F],
      preferGzipped = false,
      classLoader = None,
    )
}

object ResourceService {

  /** [[org.http4s.server.staticcontent.ResourceService]] configuration
    *
    * @param basePath prefix of the path files will be served from
    * @param pathPrefix prefix of the Uri that content will be served from
    * @param bufferSize size hint of internal buffers to use when serving resources
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    * @param preferGzipped whether to serve pre-gzipped files (with extension ".gz") if they exist
    */
  final case class Config[F[_]](
      basePath: String,
      pathPrefix: String = "",
      bufferSize: Int = 50 * 1024,
      cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
      preferGzipped: Boolean = false,
  )

}
