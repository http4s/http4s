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

import cats.effect.kernel.Async
import fs2.io.file.Files
import org.http4s.headers.`Accept-Ranges`

/** Helpers for serving static content from http4s
  *
  * Note that these tools are relatively primitive and a dedicated server should be used
  * for serious static content serving.
  */
package object staticcontent {

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files, possibly from the classpath. */
  def resourceServiceBuilder[F[_]](basePath: String): ResourceServiceBuilder[F] =
    ResourceServiceBuilder[F](basePath)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  def fileService[F[_]: Async: Files](config: FileService.Config[F]): HttpRoutes[F] =
    FileService(config)

  @deprecated("Use overload with Files constraint", "0.23.19")
  def fileService[F[_]](config: FileService.Config[F], F: Async[F]): HttpRoutes[F] =
    fileService(config)(F, Files.forAsync(F))

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files from webjars */
  def webjarServiceBuilder[F[_]]: WebjarServiceBuilder[F] =
    WebjarServiceBuilder[F]

  private[staticcontent] val AcceptRangeHeader = `Accept-Ranges`(RangeUnit.Bytes)

  // Will strip the pathPrefix from the first part of the Uri, returning the remainder without a leading '/'
  private[staticcontent] def getSubPath(uriPath: String, pathPrefix: String): String = {
    val index = pathPrefix.length + {
      if (
        uriPath.length > pathPrefix.length &&
        uriPath.charAt(pathPrefix.length) == '/'
      ) 1
      else 0
    }

    uriPath.substring(index)
  }
}
