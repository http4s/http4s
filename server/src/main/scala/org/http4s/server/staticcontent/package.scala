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

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Sync
import org.http4s.headers.`Accept-Ranges`

/** Helpers for serving static content from http4s
  *
  * Note that these tools are relatively primitive and a dedicated server should be used
  * for serious static content serving.
  */
package object staticcontent {

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files, possibly from the classpath. */
  def resourceServiceBuilder[F[_]](basePath: String, blocker: Blocker): ResourceServiceBuilder[F] =
    ResourceServiceBuilder[F](basePath, blocker)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files, possibly from the classpath. */
  @deprecated("use resourceServiceBuilder", "0.22.0-M1")
  def resourceService[F[_]: Sync: ContextShift](config: ResourceService.Config[F]): HttpRoutes[F] =
    ResourceService(config)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  def fileService[F[_]: Sync](config: FileService.Config[F]): HttpRoutes[F] =
    FileService(config)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files from webjars */
  def webjarServiceBuilder[F[_]](blocker: Blocker): WebjarServiceBuilder[F] =
    WebjarServiceBuilder[F](blocker)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files from webjars */
  @deprecated("use webjarServiceBuilder", "0.22.0-M1")
  def webjarService[F[_]: Sync: ContextShift](config: WebjarService.Config[F]): HttpRoutes[F] =
    WebjarService(config)

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
