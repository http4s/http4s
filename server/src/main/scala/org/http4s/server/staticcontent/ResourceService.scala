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

import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.all._
import java.nio.file.Paths
import org.http4s.server.middleware.TranslateUri
import org.log4s.getLogger
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

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
  private[staticcontent] def apply[F[_]](
      config: Config[F])(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] = {
    val basePath = if (config.basePath.isEmpty) "/" else config.basePath
    object BadTraversal extends Exception with NoStackTrace

    Try(Paths.get(basePath)) match {
      case Success(rootPath) =>
        TranslateUri(config.pathPrefix)(Kleisli { case request =>
          request.pathInfo.split("/") match {
            case Array(head, segments @ _*) if head.isEmpty =>
              OptionT
                .liftF(F.catchNonFatal {
                  segments.foldLeft(rootPath) {
                    case (_, "" | "." | "..") => throw BadTraversal
                    case (path, segment) =>
                      path.resolve(Uri.decode(segment, plusIsSpace = true))
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
                .recoverWith { case BadTraversal =>
                  OptionT.some(Response(Status.BadRequest))
                }
            case _ =>
              OptionT.none
          }
        })

      case Failure(e) =>
        logger.error(e)(
          s"Could not get root path from ResourceService config: basePath = ${config.basePath}, pathPrefix = ${config.pathPrefix}. All requests will fail.")
        Kleisli(_ => OptionT.pure(Response(Status.InternalServerError)))
    }
  }
}
