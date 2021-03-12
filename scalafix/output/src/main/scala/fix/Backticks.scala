package fix

import org.http4s._
import scala.concurrent.duration._

object Backticks {
  val cacheDirectives = Set(
    CacheDirective.maxAge(Duration.Zero),
    CacheDirective.maxStale(),
    CacheDirective.minFresh(Duration.Zero),
    CacheDirective.mustRevalidate,
    CacheDirective.noCache(),
    CacheDirective.noStore,
    CacheDirective.noTransform,
    CacheDirective.onlyIfCached,
    CacheDirective.proxyRevalidate,
    CacheDirective.sMaxage(Duration.Zero),
    CacheDirective.staleIfError(Duration.Zero),
    CacheDirective.staleWhileRevalidate(Duration.Zero),
  )

  val httpVersions = Set(
    HttpVersion.HTTP_1_0,
    HttpVersion.HTTP_1_1,
    HttpVersion.HTTP_2_0,
  )
}
