/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpHeader.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import java.net.InetAddress
import org.http4s.headers._

import scalaz.NonEmptyList

import org.http4s.util.{Writer, CaseInsensitiveString, Renderable, StringWriter}
import org.http4s.util.string._
import org.http4s.Charset._
import scala.util.hashing.MurmurHash3

/**
 * Abstract representation o the HTTP header
 * @see org.http4s.HeaderKey
 */
trait Header extends Renderable with Product {
  def name: CaseInsensitiveString

  def parsed: Header

  def renderValue(writer: Writer): writer.type

  def value: String = {
    val w = new StringWriter
    renderValue(w).result()
  }

  def is(key: HeaderKey): Boolean = key.matchHeader(this).isDefined

  def isNot(key: HeaderKey): Boolean = !is(key)

  override def toString = name + ": " + value

  def toRaw: RawHeader = RawHeader(name, value)

  final def render(writer: Writer): writer.type = {
    writer << name << ':' << ' '
    renderValue(writer)
  }

  final override def hashCode(): Int = MurmurHash3.mixLast(name.hashCode, MurmurHash3.productHash(parsed))

  final override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this eq h => true
    case h: Header =>
      (name == h.name) &&
      (parsed.productArity == h.parsed.productArity) &&
      (parsed.productIterator sameElements h.parsed.productIterator)
    case _ => false
  }
}

object Header {
  def unapply(header: Header): Option[(CaseInsensitiveString, String)] = Some((header.name, header.value))

  def apply(name: String, value: String): RawHeader = RawHeader(name.ci, value)

  @deprecated("Moved to org.http4s.headers.RawHeader", "0.6")
  type Raw = headers.RawHeader
  @deprecated("Moved to org.http4s.headers.RawHeader", "0.6")
  val Raw = headers.RawHeader

  @deprecated("Moved to org.http4s.headers.ParsedHeader", "0.6")
  type Parsed = headers.ParsedHeader

  @deprecated("Moved to org.http4s.headers.RecurringHeader", "0.6")
  type Recurring = headers.RecurringHeader

  @deprecated("Moved to org.http4s.headers.RecurringRenderableHeaderHeader", "0.6")
  type RecurringRenderable = headers.RecurringRenderableHeader

  @deprecated("Moved to org.http4s.headers.Accept", "0.6")
  type Accept = headers.Accept
  @deprecated("Moved to org.http4s.headers.Accept", "0.6")
  val Accept = headers.Accept

  @deprecated("Moved to org.http4s.headers.`Accept-Charset`", "0.6")
  type `Accept-Charset` = headers.`Accept-Charset`
  @deprecated("Moved to org.http4s.headers.`Accept-Charset`", "0.6")
  val `Accept-Charset` = headers.`Accept-Charset`

  @deprecated("Moved to org.http4s.headers.`Accept-Encoding`", "0.6")
  type `Accept-Encoding` = headers.`Accept-Encoding`
  @deprecated("Moved to org.http4s.headers.`Accept-Encoding`", "0.6")
  val `Accept-Encoding` = headers.`Accept-Encoding`

  @deprecated("Moved to org.http4s.headers.`Accept-Language`", "0.6")
  type `Accept-Language` = headers.`Accept-Language`
  @deprecated("Moved to org.http4s.headers.`Accept-Language`", "0.6")
  val `Accept-Language` = headers.`Accept-Language`

  @deprecated("Moved to org.http4s.headers.`Accept-Ranges`", "0.6")
  type `Accept-Ranges` = headers.`Accept-Ranges`
  @deprecated("Moved to org.http4s.headers.`Accept-Ranges`", "0.6")
  val `Accept-Ranges` = headers.`Accept-Ranges`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Allow-Credentials`", "0.6")
  val `Access-Control-Allow-Credentials` = headers.`Access-Control-Allow-Credentials`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Allow-Headers`", "0.6")
  val `Access-Control-Allow-Headers` = headers.`Access-Control-Allow-Headers`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Allow-Methods`", "0.6")
  val `Access-Control-Allow-Methods` = headers.`Access-Control-Allow-Methods`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Allow-Origin`", "0.6")
  val `Access-Control-Allow-Origin` = headers.`Access-Control-Allow-Origin`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Max-Age`", "0.6")
  val `Access-Control-Max-Age` = headers.`Access-Control-Max-Age`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Request-Headers`", "0.6")
  val `Access-Control-Request-Headers` = headers.`Access-Control-Request-Headers`

  @deprecated("Moved to org.http4s.headers.`Access-Control-Request-Method`", "0.6")
  val `Access-Control-Request-Method` = headers.`Access-Control-Request-Method`

  @deprecated("Moved to org.http4s.headers.Age", "0.6")
  val Age = headers.Age

  @deprecated("Moved to org.http4s.headers.Allow", "0.6")
  val Allow = headers.Allow

  @deprecated("Moved to org.http4s.headers.Authorization", "0.6")
  type Authorization = headers.Authorization
  @deprecated("Moved to org.http4s.headers.Authorization", "0.6")
  val Authorization = headers.Authorization

  @deprecated("Moved to org.http4s.headers.`Cache-Control`", "0.6")
  type `Cache-Control` = headers.`Cache-Control`
  @deprecated("Moved to org.http4s.headers.`Cache-Control`", "0.6")
  val `Cache-Control` = headers.`Cache-Control`

  @deprecated("Moved to org.http4s.headers.Connection", "0.6")
  type Connection = headers.Connection
  @deprecated("Moved to org.http4s.headers.Connection", "0.6")
  val Connection = headers.Connection

  @deprecated("Moved to org.http4s.headers.`Content-Base`", "0.6")
  val `Content-Base` = headers.`Content-Base`

  @deprecated("Moved to org.http4s.headers.`Content-Disposition`", "0.6")
  type `Content-Disposition` = headers.`Content-Disposition`
  @deprecated("Moved to org.http4s.headers.`Content-Disposition`", "0.6")
  val `Content-Disposition` = headers.`Content-Disposition`

  @deprecated("Moved to org.http4s.headers.`Content-Encoding`", "0.6")
  type `Content-Encoding` = headers.`Content-Encoding`
  @deprecated("Moved to org.http4s.headers.`Content-Encoding`", "0.6")
  val `Content-Encoding` = headers.`Content-Encoding`

  @deprecated("Moved to org.http4s.headers.`Content-Language`", "0.6")
  val `Content-Language` = headers.`Content-Language`

  @deprecated("Moved to org.http4s.headers.`Content-Length`", "0.6")
  type `Content-Length` = headers.`Content-Length`
  @deprecated("Moved to org.http4s.headers.`Content-Length`", "0.6")
  val `Content-Length` = headers.`Content-Length`

  @deprecated("Moved to org.http4s.headers.`Content-Location`", "0.6")
  val `Content-Location` = headers.`Content-Location`

  @deprecated("Moved to org.http4s.headers.`Content-Transfer-Encoding`", "0.6")
  val `Content-Transfer-Encoding` = headers.`Content-Transfer-Encoding`

  @deprecated("Moved to org.http4s.headers.`Content-MD5`", "0.6")
  val `Content-MD5` = headers.`Content-MD5`

  @deprecated("Moved to org.http4s.headers.`Content-Range`", "0.6")
  val `Content-Range` = headers.`Content-Range`

  @deprecated("Moved to org.http4s.headers.`Content-Type`", "0.6")
  type `Content-Type` = headers.`Content-Type`
  @deprecated("Moved to org.http4s.headers.`Content-Type`", "0.6")
  val `Content-Type` = headers.`Content-Type`

  @deprecated("Moved to org.http4s.headers.Cookie", "0.6")
  type Cookie = headers.Cookie
  @deprecated("Moved to org.http4s.headers.Cookie", "0.6")
  val Cookie = headers.Cookie

  @deprecated("Moved to org.http4s.headers.Date", "0.6")
  type Date = headers.Date
  @deprecated("Moved to org.http4s.headers.Date", "0.6")
  val Date = headers.Date

  @deprecated("Moved to org.http4s.headers.ETag", "0.6")
  type ETag = headers.ETag
  @deprecated("Moved to org.http4s.headers.ETag", "0.6")
  val ETag = headers.ETag

  @deprecated("Moved to org.http4s.headers.Expect", "0.6")
  val Expect = headers.Expect

  @deprecated("Moved to org.http4s.headers.Expires", "0.6")
  val Expires = headers.Expires

  @deprecated("Moved to org.http4s.headers.From", "0.6")
  val From = headers.From

  @deprecated("Moved to org.http4s.headers.`Front-End-Https`", "0.6")
  val `Front-End-Https` = headers.`Front-End-Https`

  @deprecated("Moved to org.http4s.headers.Host", "0.6")
  type Host = headers.Host
  @deprecated("Moved to org.http4s.headers.Host", "0.6")
  val Host = headers.Host

  @deprecated("Moved to org.http4s.headers.`If-Match`", "0.6")
  val `If-Match` = headers.`If-Match`

  @deprecated("Moved to org.http4s.headers.`If-Modified-Since`", "0.6")
  type `If-Modified-Since` = headers.`If-Modified-Since`
  @deprecated("Moved to org.http4s.headers.`If-Modified-Since`", "0.6")
  val `If-Modified-Since` = headers.`If-Modified-Since`

  @deprecated("Moved to org.http4s.headers.`If-None-Match`", "0.6")
  type `If-None-Match` = headers.`If-None-Match`
  @deprecated("Moved to org.http4s.headers.`If-None-Match`", "0.6")
  val `If-None-Match` = headers.`If-None-Match`

  @deprecated("Moved to org.http4s.headers.`If-Range`", "0.6")
  val `If-Range` = headers.`If-Range`

  @deprecated("Moved to org.http4s.headers.`If-Unmodified-Since`", "0.6")
  val `If-Unmodified-Since` = headers.`If-Unmodified-Since`

  @deprecated("Moved to org.http4s.headers.`Last-Modified`", "0.6")
  type `Last-Modified` = headers.`Last-Modified`
  @deprecated("Moved to org.http4s.headers.`Last-Modified`", "0.6")
  val `Last-Modified` = headers.`Last-Modified`

  @deprecated("Moved to org.http4s.headers.Location", "0.6")
  type Location = headers.Location
  @deprecated("Moved to org.http4s.headers.Location", "0.6")
  val Location = headers.Location

  @deprecated("Moved to org.http4s.headers.`Max-Forwards`", "0.6")
  val `Max-Forwards` = headers.`Max-Forwards`

  @deprecated("Moved to org.http4s.headers.Origin", "0.6")
  val Origin = headers.Origin

  @deprecated("Moved to org.http4s.headers.Pragma", "0.6")
  val Pragma = headers.Pragma

  @deprecated("Moved to org.http4s.headers.`Proxy-Authenticate`", "0.6")
  type `Proxy-Authenticate` = headers.`Proxy-Authenticate`
  @deprecated("Moved to org.http4s.headers.`Proxy-Authenticate`", "0.6")
  val `Proxy-Authenticate` = headers.`Proxy-Authenticate`

  @deprecated("Moved to org.http4s.headers.`Proxy-Authorization`", "0.6")
  val `Proxy-Authorization` = headers.`Proxy-Authorization`

  @deprecated("Moved to org.http4s.headers.Range", "0.6")
  val Range = headers.Range

  @deprecated("Moved to org.http4s.headers.Referer", "0.6")
  val Referer = headers.Referer

  @deprecated("Moved to org.http4s.headers.`Retry-After`", "0.6")
  val `Retry-After` = headers.`Retry-After`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Key`", "0.6")
  val `Sec-WebSocket-Key` = headers.`Sec-WebSocket-Key`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Key1`", "0.6")
  val `Sec-WebSocket-Key1` = headers.`Sec-WebSocket-Key1`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Key2`", "0.6")
  val `Sec-WebSocket-Key2` = headers.`Sec-WebSocket-Key2`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Location`", "0.6")
  val `Sec-WebSocket-Location` = headers.`Sec-WebSocket-Location`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Origin`", "0.6")
  val `Sec-WebSocket-Origin` = headers.`Sec-WebSocket-Origin`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Protocol`", "0.6")
  val `Sec-WebSocket-Protocol` = headers.`Sec-WebSocket-Protocol`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Version`", "0.6")
  val `Sec-WebSocket-Version` = headers.`Sec-WebSocket-Version`

  @deprecated("Moved to org.http4s.headers.`Sec-WebSocket-Accept`", "0.6")
  val `Sec-WebSocket-Accept` = headers.`Sec-WebSocket-Accept`

  @deprecated("Moved to org.http4s.headers.Server", "0.6")
  val Server = headers.Server

  @deprecated("Moved to org.http4s.headers.`Set-Cookie`", "0.6")
  type `Set-Cookie` = headers.`Set-Cookie`
  @deprecated("Moved to org.http4s.headers.`Set-Cookie`", "0.6")
  val `Set-Cookie` = headers.`Set-Cookie`

  @deprecated("Moved to org.http4s.headers.TE", "0.6")
  val TE = headers.TE

  @deprecated("Moved to org.http4s.headers.Trailer", "0.6")
  val Trailer = headers.Trailer

  @deprecated("Moved to org.http4s.headers.`Transfer-Encoding`", "0.6")
  type `Transfer-Encoding` = headers.`Transfer-Encoding`
  @deprecated("Moved to org.http4s.headers.`Transfer-Encoding`", "0.6")
  val `Transfer-Encoding` = headers.`Transfer-Encoding`


  @deprecated("Moved to org.http4s.headers.Upgrade", "0.6")
  val Upgrade = headers.Upgrade

  @deprecated("Moved to org.http4s.headers.`User-Agent`", "0.6")
  val `User-Agent` = headers.`User-Agent`

  @deprecated("Moved to org.http4s.headers.Vary", "0.6")
  val Vary = headers.Vary

  @deprecated("Moved to org.http4s.headers.Via", "0.6")
  val Via = headers.Via

  @deprecated("Moved to org.http4s.headers.Warning", "0.6")
  val Warning = headers.Warning

  @deprecated("Moved to org.http4s.headers.`WebSocket-Location`", "0.6")
  val `WebSocket-Location` = headers.`WebSocket-Location`

  @deprecated("Moved to org.http4s.headers.`WebSocket-Origin`", "0.6")
  val `WebSocket-Origin` = headers.`WebSocket-Origin`

  @deprecated("Moved to org.http4s.headers.`WebSocket-Protocol`", "0.6")
  val `WebSocket-Protocol` = headers.`WebSocket-Protocol`

  @deprecated("Moved to org.http4s.headers.`WWW-Authenticate`", "0.6")
  type `WWW-Authenticate` = headers.`WWW-Authenticate`
  @deprecated("Moved to org.http4s.headers.`WWW-Authenticate`", "0.6")
  val `WWW-Authenticate` = headers.`WWW-Authenticate`

  @deprecated("Moved to org.http4s.headers.`X-Forwarded-For`", "0.6")
  type `X-Forwarded-For` = headers.`X-Forwarded-For`
  @deprecated("Moved to org.http4s.headers.`X-Forwarded-For`", "0.6")
  val `X-Forwarded-For` = headers.`X-Forwarded-For`

  @deprecated("Moved to org.http4s.headers.`X-Forwarded-Proto`", "0.6")
  val `X-Forwarded-Proto` = headers.`X-Forwarded-Proto`

  @deprecated("Moved to org.http4s.headers.`X-Powered-By`", "0.6")
  val `X-Powered-By` = headers.`X-Powered-By`
}