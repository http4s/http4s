package org.http4s
package server
package staticcontent

import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import java.nio.file.{Path, Paths}
import scala.util.control.NoStackTrace

/**
  * Constructs new services to serve assets from Webjars
  */
object WebjarService {

  class WebjarServiceBuilder[F[_]] private (
      blocker: Blocker,
      webjarAssetFiltr: WebjarAssetFilter,
      cacheStrategy: CacheStrategy[F],
      classLoader: Option[ClassLoader]) {

    private def copy(
        blocker: Blocker = blocker,
        webjarAssetFiltr: WebjarAssetFilter = webjarAssetFiltr,
        cacheStrategy: CacheStrategy[F] = cacheStrategy,
        classLoader: Option[ClassLoader] = classLoader) =
      new WebjarServiceBuilder[F](blocker, webjarAssetFiltr, cacheStrategy, classLoader)

    def withWebjarAssetFilter(webjarAssetFilter: WebjarAssetFilter): WebjarServiceBuilder[F] =
      copy(webjarAssetFiltr = webjarAssetFilter)

    def withCacheStrategy(cacheStrategy: CacheStrategy[F]): WebjarServiceBuilder[F] =
      copy(cacheStrategy = cacheStrategy)

    def withClassLoader(classLoader: Option[ClassLoader]): WebjarServiceBuilder[F] =
      copy(classLoader = classLoader)

    def withBlocker(blocker: Blocker): WebjarServiceBuilder[F] =
      copy(blocker = blocker)

    def toRoutes(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] = {
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
                .filter(webjarAssetFiltr)
                .flatMap(serveWebjarAsset(blocker, cacheStrategy, classLoader, request)(_))
                .recover {
                  case BadTraversal => Response(Status.BadRequest)
                }
            case _ => OptionT.none
          }
        case _ => OptionT.none
      }
    }
  }

  object WebjarServiceBuilder {
    def apply[F[_]](blocker: Blocker) =
      new WebjarServiceBuilder(
        blocker = blocker,
        webjarAssetFiltr = _ => true,
        cacheStrategy = NoopCacheStrategy[F],
        classLoader = None)
  }

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
    * Creates a new [[HttpRoutes]] that will filter the webjars
    *
    * @param config The configuration for this service
    * @return The HttpRoutes
    */
  def apply[F[_]](blocker: Blocker): WebjarServiceBuilder[F] =
    WebjarServiceBuilder[F](blocker)

  /**
    * Returns an Option(WebjarAsset) for a Request, or None if it couldn't be mapped
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
    } else {
      None
    }
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

  /**
    * Returns an asset that matched the request if it's found in the webjar path
    *
    * @param webjarAsset The WebjarAsset
    * @param config The configuration
    * @param request The Request
    * @param optional class loader
    * @return Either the the Asset, if it exist, or Pass
    */
  private def serveWebjarAsset[F[_]: Sync: ContextShift](
      blocker: Blocker,
      cacheStrategy: CacheStrategy[F],
      classLoader: Option[ClassLoader],
      request: Request[F])(webjarAsset: WebjarAsset): OptionT[F, Response[F]] =
    StaticFile
      .fromResource(webjarAsset.pathInJar, blocker, Some(request), classloader = classLoader)
      .semiflatMap(cacheStrategy.cache(request.pathInfo, _))
}
