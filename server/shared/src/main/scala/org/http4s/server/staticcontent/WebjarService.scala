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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.kernel.Async
import cats.syntax.all._
import org.http4s.internal.CollectionCompat.CollectionConverters._

import java.nio.file.Path
import java.nio.file.Paths
import scala.util.control.NoStackTrace

/** [[org.http4s.server.staticcontent.WebjarServiceBuilder]] builder
  *
  * @param cacheStrategy strategy to use for caching purposes.
  * @param classLoader optional classloader for extracting the resources
  * @param preferGzipped prefer gzip compression format?
  * @param webjarAssetFilter To filter which assets from the webjars should be served
  */
class WebjarServiceBuilder[F[_]] private (
    webjarAssetFilter: WebjarServiceBuilder.WebjarAssetFilter,
    cacheStrategy: CacheStrategy[F],
    classLoader: Option[ClassLoader],
    preferGzipped: Boolean,
) {

  import WebjarServiceBuilder.{WebjarAsset, WebjarAssetFilter, serveWebjarAsset}

  private def copy(
      webjarAssetFilter: WebjarAssetFilter = webjarAssetFilter,
      cacheStrategy: CacheStrategy[F] = cacheStrategy,
      classLoader: Option[ClassLoader] = classLoader,
      preferGzipped: Boolean = preferGzipped,
  ) =
    new WebjarServiceBuilder[F](webjarAssetFilter, cacheStrategy, classLoader, preferGzipped)

  def withWebjarAssetFilter(webjarAssetFilter: WebjarAssetFilter): WebjarServiceBuilder[F] =
    copy(webjarAssetFilter = webjarAssetFilter)

  def withCacheStrategy(cacheStrategy: CacheStrategy[F]): WebjarServiceBuilder[F] =
    copy(cacheStrategy = cacheStrategy)

  def withClassLoader(classLoader: Option[ClassLoader]): WebjarServiceBuilder[F] =
    copy(classLoader = classLoader)

  def withPreferGzipped(preferGzipped: Boolean): WebjarServiceBuilder[F] =
    copy(preferGzipped = preferGzipped)

  def toRoutes(implicit F: Async[F]): HttpRoutes[F] = {
    object BadTraversal extends Exception with NoStackTrace
    val Root = Paths.get("")
    Kleisli {
      // Intercepts the routes that match webjar asset names
      case request if request.method == Method.GET =>
        val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
        OptionT
          .liftF(F.catchNonFatal {
            segments.foldLeft(Root) {
              case (_, "" | "." | "..") => throw BadTraversal
              case (path, segment) =>
                path.resolve(segment)
            }
          })
          .subflatMap(toWebjarAsset)
          .filter(webjarAssetFilter)
          .flatMap(serveWebjarAsset(cacheStrategy, classLoader, request, preferGzipped)(_))
          .recover { case BadTraversal =>
            Response(Status.BadRequest)
          }
      case _ => OptionT.none
    }
  }

  /** Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
    *
    * @param p The request path without the prefix
    * @return The WebjarAsset, or None if it couldn't be mapped
    */
  private def toWebjarAsset(p: Path): Option[WebjarAsset] = {
    val count = p.getNameCount
    if (count > 2) {
      val library = p.getName(0).toString
      val version = p.getName(1).toString
      val asset = asScalaIterator(p.subpath(2, count).iterator()).mkString("/")
      Some(WebjarAsset(library, version, asset))
    } else
      None
  }

  /** Creates a scala.Iterator from a java.util.Iterator.
    *
    * We're not using scala.jdk.CollectionConverters (which was added in 2.13)
    * or scala.collection.convert.ImplicitConversion (which was deprecated in 2.13)
    * to ease cross-building against multiple Scala versions.
    */
  private def asScalaIterator[A](underlying: java.util.Iterator[A]): Iterator[A] =
    new Iterator[A] {
      override def hasNext: Boolean = underlying.hasNext
      override def next(): A = underlying.next()
    }

}

object WebjarServiceBuilder {
  def apply[F[_]] =
    new WebjarServiceBuilder(
      webjarAssetFilter = _ => true,
      cacheStrategy = NoopCacheStrategy[F],
      classLoader = None,
      preferGzipped = false,
    )

  /** A filter callback for Webjar asset
    * It's a function that takes the WebjarAsset and returns whether or not the asset
    * should be served to the client.
    */
  type WebjarAssetFilter = WebjarAsset => Boolean

  /** Contains the information about an asset inside a webjar
    *
    * @param library The webjar's library name
    * @param version The version of the webjar
    * @param asset The asset name inside the webjar
    */
  final case class WebjarAsset(library: String, version: String, asset: String) {

    /** Constructs a full path for an asset inside a webjar asset
      *
      * @return The full name in the Webjar
      */
    private[staticcontent] lazy val pathInJar: String =
      s"/META-INF/resources/webjars/$library/$version/$asset"
  }

  /** Returns an asset that matched the request if it's found in the webjar path
    *
    * @param request The Request
    * @param preferGzipped prefer gzip compression format?
    * @param webjarAsset The WebjarAsset
    * @return Either the the Asset, if it exist, or Pass
    */
  private def serveWebjarAsset[F[_]](
      cacheStrategy: CacheStrategy[F],
      classLoader: Option[ClassLoader],
      request: Request[F],
      preferGzipped: Boolean,
  )(webjarAsset: WebjarAsset)(implicit F: Async[F]): OptionT[F, Response[F]] =
    StaticFile
      .fromResource(
        webjarAsset.pathInJar,
        Some(request),
        classloader = classLoader,
        preferGzipped = preferGzipped,
      )
      .semiflatMap(cacheStrategy.cache(request.pathInfo, _))
}

object WebjarService {

  /** [[org.http4s.server.staticcontent.WebjarService]] configuration
    *
    * @param filter To filter which assets from the webjars should be served
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](
      filter: WebjarAssetFilter = _ => true,
      cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
  )

  /** Contains the information about an asset inside a webjar
    *
    * @param library The webjar's library name
    * @param version The version of the webjar
    * @param asset The asset name inside the webjar
    */
  final case class WebjarAsset(library: String, version: String, asset: String) {

    /** Constructs a full path for an asset inside a webjar asset
      *
      * @return The full name in the Webjar
      */
    private[staticcontent] lazy val pathInJar: String =
      s"/META-INF/resources/webjars/$library/$version/$asset"
  }

  /** A filter callback for Webjar asset
    * It's a function that takes the WebjarAsset and returns whether or not the asset
    * should be served to the client.
    */
  type WebjarAssetFilter = WebjarAsset => Boolean

  /** Creates a new [[HttpRoutes]] that will filter the webjars
    *
    * @param config The configuration for this service
    * @return The HttpRoutes
    */
  @deprecated("use WebjarServiceBuilder", "1.0.0-M1")
  def apply[F[_]](config: Config[F])(implicit F: Async[F]): HttpRoutes[F] = {
    object BadTraversal extends Exception with NoStackTrace
    val Root = Paths.get("")
    Kleisli {
      // Intercepts the routes that match webjar asset names
      case request if request.method == Method.GET && request.pathInfo.nonEmpty =>
        val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
        OptionT
          .liftF(F.catchNonFatal {
            segments.foldLeft(Root) {
              case (_, "" | "." | "..") => throw BadTraversal
              case (path, segment) =>
                path.resolve(segment)
            }
          })
          .subflatMap(toWebjarAsset)
          .filter(config.filter)
          .flatMap(serveWebjarAsset(config, request)(_))
          .recover { case BadTraversal =>
            Response(Status.BadRequest)
          }
      case _ => OptionT.none
    }
  }

  /** Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
    *
    * @param p The request path without the prefix
    * @return The WebjarAsset, or None if it couldn't be mapped
    */
  private def toWebjarAsset(p: Path): Option[WebjarAsset] = {
    val count = p.getNameCount
    if (count > 2) {
      val library = p.getName(0).toString
      val version = p.getName(1).toString
      val asset = p.subpath(2, count).iterator().asScala.mkString("/")
      Some(WebjarAsset(library, version, asset))
    } else
      None
  }

  /** Returns an asset that matched the request if it's found in the webjar path
    *
    * @param webjarAsset The WebjarAsset
    * @param config The configuration
    * @param request The Request
    * @return Either the the Asset, if it exist, or Pass
    */
  private def serveWebjarAsset[F[_]: Async](config: Config[F], request: Request[F])(
      webjarAsset: WebjarAsset
  ): OptionT[F, Response[F]] =
    StaticFile
      .fromResource(webjarAsset.pathInJar, Some(request))
      .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
}
