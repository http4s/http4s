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
    CacheDirective.`private`(),
    CacheDirective.public,
    CacheDirective.`proxy-revalidate`,
    CacheDirective.`s-maxage`(Duration.Zero),
    CacheDirective.`stale-if-error`(Duration.Zero),
    CacheDirective.`stale-while-revalidate`(Duration.Zero),
  )

  val charsets = Set(
    Charset.`US-ASCII`,
    Charset.`ISO-8859-1`,
    Charset.`UTF-8`,
    Charset.`UTF-16`,
    Charset.`UTF-16BE`,
    Charset.`UTF-16LE`,
  )

  val charsetRangeAll = CharsetRange.`*`

  val contentCodings = Set(
    ContentCoding.`*`,
    ContentCoding.aes128gcm,
    ContentCoding.br,
    ContentCoding.compress,
    ContentCoding.deflate,
    ContentCoding.exi,
    ContentCoding.gzip,
    ContentCoding.identity,
    ContentCoding.`pack200-gzip`,
    ContentCoding.zstd,
    ContentCoding.`x-compress`,
    ContentCoding.`x-gzip`,
  )

  val httpVersions = Set(
    HttpVersion.`HTTP/1.0`,
    HttpVersion.`HTTP/1.1`,
    HttpVersion.`HTTP/2.0`,
  )

  val languageTagAll = LanguageTag.`*`

  val mediaRanges = Set(
    MediaRange.`*/*`,
    MediaRange.`application/*`,
    MediaRange.`audio/*`,
    MediaRange.`image/*`,
    MediaRange.`message/*`,
    MediaRange.`multipart/*`,
    MediaRange.`text/*`,
    MediaRange.`video/*`,
  )
}
