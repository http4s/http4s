package org.http4s
package server

import cats.effect.{Blocker, ContextShift, Sync}
import org.http4s.headers.`Accept-Ranges`

/** Helpers for serving static content from http4s
  *
  * Note that these tools are relatively primitive and a dedicated server should be used
  * for serious static content serving.
  */
package object staticcontent {

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files, possibly from the classpath. */
  def resourceService[F[_]: Sync: ContextShift](
      config: ResourceService.Config[F],
      classLoader: Option[ClassLoader] = None): HttpRoutes[F] =
    ResourceService(config, classLoader)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  def fileService[F[_]: Sync](config: FileService.Config[F]): HttpRoutes[F] =
    FileService(config)

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files from webjars */
  def webjarService[F[_]: Sync: ContextShift](
      blocker: Blocker): WebjarService.WebjarServiceBuilder[F] =
    WebjarService(blocker)

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
