/*
rule = v0_22
*/
package fix

import org.http4s._
import scala.concurrent.duration._

object Backticks {
  val cacheDirectives = Set(
    CacheDirective.`max-age`(Duration.Zero),
    CacheDirective.`max-stale`(),
    CacheDirective.`min-fresh`(Duration.Zero),
    CacheDirective.`must-revalidate`,
    CacheDirective.`no-cache`(),
    CacheDirective.`no-store`,
    CacheDirective.`no-transform`,
    CacheDirective.`only-if-cached`,
    CacheDirective.`proxy-revalidate`,
    CacheDirective.`s-maxage`(Duration.Zero),
    CacheDirective.`stale-if-error`(Duration.Zero),
    CacheDirective.`stale-while-revalidate`(Duration.Zero),
  )

  val httpVersions = Set(
    HttpVersion.`HTTP/1.0`,
    HttpVersion.`HTTP/1.1`,
    HttpVersion.`HTTP/2.0`,
  )
}
