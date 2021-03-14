package fix

import org.http4s._
import scala.concurrent.duration._

object Backticks {
  val cacheDirectives = Set(
    CacheDirective.MaxAge(Duration.Zero),
    CacheDirective.MaxStale(),
    CacheDirective.MinFresh(Duration.Zero),
    CacheDirective.MustRevalidate,
    CacheDirective.NoCache(),
    CacheDirective.NoStore,
    CacheDirective.NoTransform,
    CacheDirective.OnlyIfCached,
    CacheDirective.Private(),
    CacheDirective.Public,
    CacheDirective.ProxyRevalidate,
    CacheDirective.SMaxage(Duration.Zero),
    CacheDirective.StaleIfError(Duration.Zero),
    CacheDirective.StaleWhileRevalidate(Duration.Zero),
  )

  val charsets = Set(
    Charset.UsAscii,
    Charset.Iso8859_1,
    Charset.Utf8,
    Charset.Utf16,
    Charset.Utf16be,
    Charset.Utf16le,
  )

  val httpVersions = Set(
    HttpVersion.Http1_0,
    HttpVersion.Http1_1,
    HttpVersion.Http2_0,
  )
}
