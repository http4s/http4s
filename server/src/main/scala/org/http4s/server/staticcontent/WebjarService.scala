package org.http4s
package server
package staticcontent

import java.util.concurrent.ExecutorService

import scalaz.concurrent.{Strategy, Task}

/**
  * Constructs new services to serve assets from Webjars
  */
object WebjarService {

  /** [[org.http4s.server.staticcontent.WebjarService]] configuration
    *
    * @param filter To filter which assets from the webjars should be served
    * @param pathPrefix prefix of the Uri that content will be served from
    * @param executor `ExecutorService` to use when collecting content
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config(filter: WebjarAssetFilter = _ => true,
                          pathPrefix: String = "",
                          executor: ExecutorService = Strategy.DefaultExecutorService,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy)

  /**
    * Contains the information about an asset inside a webjar
    *
    * @param library The webjar's library name
    * @param version The version of the webjar
    * @param asset The asset name inside the webjar
    */
  final case class WebjarAsset(library: String, version: String, asset: String, pathPrefix: String) {

    /**
      * The path under which the asset is reachable in the web
      *
      * @return The path where the asset is reachable on the web
      */
    private lazy val webPath: String =
      s"${ensureSlash(pathPrefix)}$library/$version/$asset"

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
  def apply(config: Config): HttpService = Service.lift {

    // Intercepts the routes that match webjar asset names
    case request if request.method == Method.GET =>
      Option(request.pathInfo)
          .filter(filterPathPrefix(config))
          .map(removePathPrefix(config))
          .map(sanitize)
          .flatMap(toWebjarAsset(config))
          .filter(config.filter)
          .map(serveWebjarAsset(config, request))
          .getOrElse(Pass.now)

  }

  /**
    * Returns whether the path starts with the pathPrefix
    *
    * @param config The configuration
    * @param path The path
    * @return true, if the path starts with the pathPrefix
    */
  private def filterPathPrefix(config: Config)(path: String): Boolean =
    path.startsWith(ensureSlash(config.pathPrefix))

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
    * Removes the pathPrefix from a path
    *
    * @param config The configuration
    * @param path The path to remove the pathPrefix from
    * @return The path without the pathPrefix
    */
  private def removePathPrefix(config: Config)(path: String): String =
    getSubPath(path, config.pathPrefix)

  /**
    * Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
    *
    * @param config The configuration
    * @param subPath The request path without the prefix
    * @return The WebjarAsset, or None if it couldn't be mapped
    */
  private def toWebjarAsset(config: Config)(subPath: String): Option[WebjarAsset] =
    Option(subPath)
      .map(_.split('/').toList)
      .filter(_.size >= 3)
      .map(parts => WebjarAsset(parts.head, parts(1), parts.drop(2).mkString("/"), config.pathPrefix))
      .filter(_.library.nonEmpty)
      .filter(_.version.nonEmpty)
      .filter(_.asset.nonEmpty)

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
