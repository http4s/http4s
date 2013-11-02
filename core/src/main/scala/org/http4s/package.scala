package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.{Enumeratee, Iteratee}
import scala.language.implicitConversions
import concurrent.ExecutionContext
import com.typesafe.config.{ConfigFactory, Config}
import org.joda.time.{DateTime, DateTimeZone, ReadableInstant}
import org.joda.time.format.DateTimeFormat
import java.util.Locale
import org.http4s.util.{LowercaseEn, LowercaseSyntax}
import scalaz.@@

package object http4s extends LowercaseSyntax {
  type Route = PartialFunction[RequestPrelude, Iteratee[Chunk, Responder]]

  type ResponderBody = Enumeratee[Chunk, Chunk]

  type Middleware = (Route => Route)

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

  implicit def string2headerkey(name: String): HeaderKey[Header] = Headers.Key(name)

  val Get = Methods.Get
  val Post = Methods.Post
  val Put = Methods.Put
  val Delete = Methods.Delete
  val Trace = Methods.Trace
  val Options = Methods.Options
  val Patch = Methods.Patch
  val Head = Methods.Head
  val Connect = Methods.Connect

  private[this] val Rfc1123Format = DateTimeFormat
    .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
    .withLocale(Locale.US)
    .withZone(DateTimeZone.UTC);

  implicit class RichReadableInstant(instant: ReadableInstant) {
    def formatRfc1123: String = Rfc1123Format.print(instant)
  }

  val UnixEpoch = new DateTime(0)
}
