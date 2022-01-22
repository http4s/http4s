package org.http4s
package server
package staticcontent

private[staticcontent] trait FileServiceConfigCompanionPlatform {
  @deprecated(
    "Config is no longer a case class. The Config.unapply method is provided for binary compatibility.",
    "0.23.8",
  )
  def unapply[F[_]](
      config: FileService.Config[F]
  ): Option[(String, FileService.PathCollector[F], String, Int, CacheStrategy[F])] =
    Some(
      (
        config.systemPath,
        config.pathCollector,
        config.pathPrefix,
        config.bufferSize,
        config.cacheStrategy,
      )
    )
}
