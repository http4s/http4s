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

    private[staticcontent] def webPath: String =
      s"$pathPrefix/$library/$version/$asset"

    /**
      * Constructs a full path for an asset inside a webjar asset
      *
      * @return The full name in the Webjar
      */
    private[staticcontent] def pathInJar: String =
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
  def apply(config: Config): HttpService = Service.lift { request =>

    // Intercepts the routes that match webjar asset names
    Option(request.pathInfo)
        .filter(_.startsWith(config.pathPrefix))
        .map(getSubPath(_, config.pathPrefix))
        .map(sanitize)
        .flatMap(toWebjarAsset(_, config))
        .filter(config.filter)
        .flatMap(serveWebjarAsset(request, _, config))
        .getOrElse(Pass.now)

  }

  /**
    * Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
    *
    * @param subPath The request path without the prefix
    * @return The WebjarAsset, or None if it couldn't be mapped
    */
  private def toWebjarAsset(subPath: String, config: Config): Option[WebjarAsset] =
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
    * @param request The Request
    * @param webjarAsset The WebjarAsset
    * @return Either the the Asset, if it exist, or Pass
    */
  private def serveWebjarAsset(request: Request, webjarAsset: WebjarAsset, config: Config): Option[Task[MaybeResponse]] =
    Some(
      StaticFile
        .fromResource(webjarAsset.pathInJar, Some(request))
        .fold(Pass.now)(config.cacheStrategy.cache(request.pathInfo, _))
    )

}
