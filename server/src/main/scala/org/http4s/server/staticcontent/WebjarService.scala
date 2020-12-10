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

import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.all._
import java.nio.file.{Path, Paths}
import org.http4s.internal.CollectionCompat.CollectionConverters._
import scala.util.control.NoStackTrace

/** Constructs new services to serve assets from Webjars
  */
object WebjarService {

  /** [[org.http4s.server.staticcontent.WebjarService]] configuration
    *
    * @param blocker execution context for blocking I/O
    * @param filter To filter which assets from the webjars should be served
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](
      blocker: Blocker,
      filter: WebjarAssetFilter = _ => true,
      cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F])

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
  def apply[F[_]](config: Config[F])(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] = {
    object BadTraversal extends Exception with NoStackTrace
    val Root = Paths.get("")
    Kleisli {
      // Intercepts the routes that match webjar asset names
      case request if request.method == Method.GET =>
        request.pathInfo.split("/") match {
          case Array(head, segments @ _*) if head.isEmpty =>
            OptionT
              .liftF(F.catchNonFatal {
                segments.foldLeft(Root) {
                  case (_, "" | "." | "..") => throw BadTraversal
                  case (path, segment) =>
                    path.resolve(Uri.decode(segment, plusIsSpace = true))
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
  private def serveWebjarAsset[F[_]: Sync: ContextShift](config: Config[F], request: Request[F])(
      webjarAsset: WebjarAsset): OptionT[F, Response[F]] =
    StaticFile
      .fromResource(webjarAsset.pathInJar, config.blocker, Some(request))
      .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
}
