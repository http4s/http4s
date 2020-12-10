/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.effect.kernel.{Async, Sync}
import org.http4s.headers.`Accept-Ranges`

/** Helpers for serving static content from http4s
  *
  * Note that these tools are relatively primitive and a dedicated server should be used
  * for serious static content serving.
  */
package object staticcontent {

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files, possibly from the classpath. */
  def resourceServiceBuilder[F[_]: Sync](basePath: String): ResourceServiceBuilder[F] =
    ResourceServiceBuilder[F](basePath)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files, possibly from the classpath. */
  @deprecated("use resourceServiceBuilder", "1.0.0-M1")
  def resourceService[F[_]: Async](config: ResourceService.Config[F]): HttpRoutes[F] =
    ResourceService(config)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  def fileService[F[_]: Async](config: FileService.Config[F]): HttpRoutes[F] =
    FileService(config)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files from webjars */
  def webjarServiceBuilder[F[_]]: WebjarServiceBuilder[F] =
    WebjarServiceBuilder[F]

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files from webjars */
  @deprecated("use webjarServiceBuilder", "1.0.0-M1")
  def webjarService[F[_]: Async](config: WebjarService.Config[F]): HttpRoutes[F] =
    WebjarService(config)

  private[staticcontent] val AcceptRangeHeader = `Accept-Ranges`(RangeUnit.Bytes)

  // Will strip the pathPrefix from the first part of the Uri, returning the remainder without a leading '/'
  private[staticcontent] def getSubPath(uriPath: String, pathPrefix: String): String = {
    val index = pathPrefix.length + {
      if (uriPath.length > pathPrefix.length &&
        uriPath.charAt(pathPrefix.length) == '/') 1
      else 0
    }

    uriPath.substring(index)
  }
}
