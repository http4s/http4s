package fix

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
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

  val charsetRangeAll = CharsetRange.All

  val contentCodings = Set(
    ContentCoding.All,
    ContentCoding.Aes128gcm,
    ContentCoding.Br,
    ContentCoding.Compress,
    ContentCoding.Deflate,
    ContentCoding.Exi,
    ContentCoding.Gzip,
    ContentCoding.Identity,
    ContentCoding.Pack200Gzip,
    ContentCoding.Zstd,
    ContentCoding.XCompress,
    ContentCoding.XGzip,
  )

  val httpVersions = Set(
    HttpVersion.Http1_0,
    HttpVersion.Http1_1,
    HttpVersion.Http2_0,
  )

  val languageTagAll = LanguageTag.All

  val mediaRanges = Set(
    MediaRange.All,
    MediaRange.AllApplications,
    MediaRange.AllAudio,
    MediaRange.AllImages,
    MediaRange.AllMessages,
    MediaRange.AllMultipart,
    MediaRange.AllText,
    MediaRange.AllVideo,
  )

  val methods = Set(
    Method.Acl,
    Method.BaselineControl,
    Method.Bind,
    Method.Checkin,
    Method.Checkout,
    Method.Connect,
    Method.Copy,
    Method.Delete,
    Method.Get,
    Method.Head,
    Method.Label,
    Method.Link,
    Method.Lock,
    Method.Merge,
    Method.MkActivity,
    Method.MkCalendar,
    Method.MkCol,
    Method.MkRedirectRef,
    Method.MkWorkspace,
    Method.Move,
    Method.Options,
    Method.OrderPatch,
    Method.Patch,
    Method.Post,
    Method.Pri,
    Method.PropFind,
    Method.PropPatch,
    Method.Put,
    Method.Rebind,
    Method.Report,
    Method.Search,
    Method.Trace,
    Method.Unbind,
    Method.Uncheckout,
    Method.Unlink,
    Method.Unlock,
    Method.Update,
    Method.UpdateDirectRef,
    Method.VersionControl,
  )

  val transferCodings = Set(
    TransferCoding.Chunked,
    TransferCoding.Compress,
    TransferCoding.Deflate,
    TransferCoding.Gzip,
    TransferCoding.Identity,
  )

  val dslMethods = HttpRoutes.of[IO] {
    case Get -> _ => NoContent()
    case Head -> _ => NoContent()
    case Post -> _ => NoContent()
    case Put -> _ => NoContent()
    case Delete -> _ => NoContent()
    case Connect -> _ => NoContent()
    case Options -> _ => NoContent()
    case Trace -> _ => NoContent()
    case Patch -> _ => NoContent()
  }
}
