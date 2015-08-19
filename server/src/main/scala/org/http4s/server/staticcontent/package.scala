package org.http4s
package server

import org.http4s.headers.`Accept-Ranges`

/** Helpers for serving static content from http4s
  *
  * Note that these tools are relatively primitive and a dedicated server should be used
  * for serious static content serving.
  */
package object staticcontent {

  /** Make a new [[org.http4s.server.HttpService]] that serves static files, possibly from the classpath. */
  def resourceService(config: ResourceService.Config): HttpService = ResourceService(config)

  /** Make a new [[org.http4s.server.HttpService]] that serves static files. */
  def fileService(config: FileService.Config): HttpService = FileService(config)

  private[staticcontent] val sanitize = "\\.\\.".r.replaceAllIn(_: String, ".")

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
