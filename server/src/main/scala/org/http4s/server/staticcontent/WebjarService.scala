package org.http4s
package server
package staticcontent

import fs2.Task

/**
  * Constructs new services to serve assets from Webjars
  */
object WebjarService {

  /** [[org.http4s.server.staticcontent.WebjarService]] configuration
    *
    * @param filter To filter which assets from the webjars should be served
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config(filter: WebjarAssetFilter = _ => true,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy)

  /**
    * Contains the information about an asset inside a webjar
    *
    * @param library The webjar's library name
    * @param version The version of the webjar
    * @param asset The asset name inside the webjar
    */
  final case class WebjarAsset(library: String, version: String, asset: String) {

    /**
      * Constructs a full path for an asset inside a webjar asset
      *
      * @return The full name in the Webjar
      */
    private[staticcontent] lazy val pathInJar: String =
      s"/META-INF/resources/webjars/$library/$version/$asset"

  }

  /**
    * A filter callback for Webjar asset
    * It's a function that takes the WebjarAsset and returns whether or not the asset
    * should be served to the client.
    */
  type WebjarAssetFilter = WebjarAsset => Boolean

  /**
    * Creates a new HttpService that will filter the webjars
    *
    * @param config The configuration for this service
    * @return The HttpService
    */
  def apply(config: Config): HttpService = Service {
    // Intercepts the routes that match webjar asset names
    case request if request.method == Method.GET =>
      Option(request.pathInfo)
          .map(sanitize)
          .flatMap(toWebjarAsset)
          .filter(config.filter)
          .map(serveWebjarAsset(config, request))
          .getOrElse(Pass.now)
  }

  /**
    * Ensures there is a slash at the end of the path
    *
    * @param path The path
    * @return The path, ending with a slash
    */
  private def ensureSlash(path: String): String =
    if(path.nonEmpty && !path.endsWith("/"))
      path + "/"
    else
      path

  /**
    * Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
    *
    * @param subPath The request path without the prefix
    * @return The WebjarAsset, or None if it couldn't be mapped
    */
  private def toWebjarAsset(subPath: String): Option[WebjarAsset] =
    Option(subPath)
      .map(_.split("/", 4))
      .collect {
        case Array("", library, version, asset)
            if library.nonEmpty && version.nonEmpty && asset.nonEmpty =>
          WebjarAsset(library, version, asset)
      }

  /**
    * Returns an asset that matched the request if it's found in the webjar path
    *
    * @param webjarAsset The WebjarAsset
    * @param config The configuration
    * @param request The Request
    * @return Either the the Asset, if it exist, or Pass
    */
  private def serveWebjarAsset(config: Config, request: Request)(webjarAsset: WebjarAsset): Task[MaybeResponse] =
    StaticFile
      .fromResource(webjarAsset.pathInJar, Some(request))
      .fold(Pass.now)(config.cacheStrategy.cache(request.pathInfo, _))
}
