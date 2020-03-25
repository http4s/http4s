package org.http4s
package server
package staticcontent

import cats.data.{Kleisli, OptionT}
import cats.effect.Effect
import cats.implicits._
import java.nio.file.{Path, Paths}
import org.http4s.util.UrlCodingUtils.urlDecode
import scala.util.control.NoStackTrace

/**
  * Constructs new services to serve assets from Webjars
  */
object WebjarService {

  /** [[org.http4s.server.staticcontent.WebjarService]] configuration
    *
    * @param filter To filter which assets from the webjars should be served
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](
      filter: WebjarAssetFilter = _ => true,
      cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F])

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
  def apply[F[_]](config: Config[F])(implicit F: Effect[F]): HttpService[F] = {
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
                    path.resolve(urlDecode(segment, plusIsSpace = true))
                }
              })
              .subflatMap(toWebjarAsset)
              .filter(config.filter)
              .flatMap(serveWebjarAsset(config, request)(_))
              .recover {
                case BadTraversal => Response(Status.BadRequest)
              }
          case _ => OptionT.none
        }
      case _ => OptionT.none
    }
  }

  /**
    * Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
    *
    * @param subPath The request path without the prefix
    * @return The WebjarAsset, or None if it couldn't be mapped
    */
  private def toWebjarAsset(p: Path): Option[WebjarAsset] = {
    val count = p.getNameCount
    if (count > 2) {
      val library = p.getName(0).toString
      val version = p.getName(1).toString
      val asset = p.subpath(2, count)
      Some(WebjarAsset(library, version, asset.toString))
    } else {
      None
    }
  }

  /**
    * Returns an asset that matched the request if it's found in the webjar path
    *
    * @param webjarAsset The WebjarAsset
    * @param config The configuration
    * @param request The Request
    * @return Either the the Asset, if it exist, or Pass
    */
  private def serveWebjarAsset[F[_]: Effect](config: Config[F], request: Request[F])(
      webjarAsset: WebjarAsset): OptionT[F, Response[F]] =
    StaticFile
      .fromResource(webjarAsset.pathInJar, Some(request))
      .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
}
