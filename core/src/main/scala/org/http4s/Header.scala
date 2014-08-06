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
import scalaz.NonEmptyList

import org.http4s.util.{Writer, CaseInsensitiveString, Renderable, ValueRenderable, StringWriter}
import org.http4s.util.string._
import org.http4s.Charset._
import scala.util.hashing.MurmurHash3

/**
 * Abstract representation o the HTTP header
 * @see org.http4s.HeaderKey
 */
sealed trait Header extends ValueRenderable with Product {
  def name: CaseInsensitiveString

  def parsed: Header

  def value = stringValue

  def is(key: HeaderKey): Boolean = key.matchHeader(this).isDefined

  def isNot(key: HeaderKey): Boolean = !is(key)

  override def toString = name + ": " + value

  def toRaw: Header.Raw = Header.Raw(name, value)

  override def render[W <: Writer](writer: W): writer.type = {
    writer ~ name ~ ':' ~ ' '
    renderValue(writer)
  }

  final override def hashCode(): Int = MurmurHash3.mixLast(name.hashCode, MurmurHash3.productHash(parsed))

  override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this eq h => true
    case h: Header =>
      (name == h.name) &&
      (parsed.productArity == h.parsed.productArity) &&
      (parsed.productIterator sameElements h.parsed.productIterator)
    case _ => false
  }
}

object Header {
  def unapply(header: Header): Option[(CaseInsensitiveString, String)] = Some((header.name, header.stringValue))

  def apply(name: String, value: String): Raw = Raw(name.ci, value)

  /**
   * Raw representation of the Header
   *
   * This can be considered the simplest representation where the header is specified as the product of
   * a key and a value
   * @param name case-insensitive string used to identify the header
   * @param value String representation of the header value
   */
  final case class Raw(name: CaseInsensitiveString, override val value: String) extends Header {
    override def stringValue = value
    override lazy val parsed = parser.HttpParser.parseHeader(this).getOrElse(this)
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(stringValue)
  }

  /** A Header that is already parsed from its String representation. */
  trait Parsed extends Header {
    def key: HeaderKey
    def name = key.name
    def parsed: this.type = this
  }

  /**
   * A recurring header that satisfies this clause of the Spec:
   *
   * Multiple message-header fields with the same field-name MAY be present in a message if and only if the entire
   * field-value for that header field is defined as a comma-separated list [i.e., #(values)]. It MUST be possible
   * to combine the multiple header fields into one "field-name: field-value" pair, without changing the semantics
   * of the message, by appending each subsequent field-value to the first, each separated by a comma.
   */
  trait Recurring extends Parsed {
    type Value
    def key: HeaderKey.Recurring
    def values: NonEmptyList[Value]
  }

  /** Simple helper trait that provides a default way of rendering the value */
  trait RecurringRenderable extends Recurring {
    type Value <: Renderable
    def renderValue[W <: Writer](writer: W): writer.type = {
      values.head.render(writer)
      values.tail.foreach( writer ~ ", " ~ _ )
      writer
    }
  }

  object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring
  final case class Accept(values: NonEmptyList[MediaRange]) extends RecurringRenderable {
    def key = Accept
    type Value = MediaRange
  }

  object `Accept-Charset` extends HeaderKey.Internal[`Accept-Charset`] with HeaderKey.Recurring
  final case class `Accept-Charset`(values: NonEmptyList[CharsetRange]) extends RecurringRenderable {
    def key = `Accept-Charset`
    type Value = CharsetRange

    def qValue(charset: Charset): QValue = {
      def specific = values.list.collectFirst { case cs: CharsetRange.Atom => cs.qValue }
      def splatted = values.list.collectFirst { case cs: CharsetRange.`*` => cs.qValue }
      def default = if (charset == Charset.`ISO-8859-1`) QValue.One else QValue.Zero
      specific orElse splatted getOrElse default
    }

    def isSatisfiedBy(charset: Charset) = qValue(charset) > QValue.Zero

    def map(f: CharsetRange => CharsetRange): `Accept-Charset` = `Accept-Charset`(values.map(f))
  }

  object `Accept-Encoding` extends HeaderKey.Internal[`Accept-Encoding`] with HeaderKey.Recurring
  final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding]) extends RecurringRenderable {
    def key = `Accept-Encoding`
    type Value = ContentCoding
    def preferred: ContentCoding = values.tail.fold(values.head)((a, b) => if (a.qValue >= b.qValue) a else b)
    def satisfiedBy(coding: ContentCoding): Boolean = values.list.find(_.satisfiedBy(coding)).isDefined
  }

  object `Accept-Language` extends HeaderKey.Internal[`Accept-Language`] with HeaderKey.Recurring
  final case class `Accept-Language`(values: NonEmptyList[LanguageTag]) extends RecurringRenderable {
    def key = `Accept-Language`
    type Value = LanguageTag
    def preferred: LanguageTag = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
    def satisfiedBy(languageTag: LanguageTag) = values.list.find(_.satisfiedBy(languageTag)).isDefined
  }

  // TODO Interpreting this as not a recurring header, because of "none".
  object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {
    def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
    def bytes = apply(RangeUnit.bytes)
    def none = apply(Nil)
  }
  final case class `Accept-Ranges` private[http4s] (rangeUnits: Seq[RangeUnit]) extends Parsed {
    def key = `Accept-Ranges`
    def renderValue[W <: Writer](writer: W): writer.type = {
      if (rangeUnits.isEmpty) writer.append("none")
      else {
        writer.append(rangeUnits.head)
        rangeUnits.tail.foreach(r => writer.append(", ").append(r))
        writer
      }
    }
  }

  object `Accept-Patch` extends HeaderKey.Default

  object `Access-Control-Allow-Credentials` extends HeaderKey.Default

  object `Access-Control-Allow-Headers` extends HeaderKey.Default

  object `Access-Control-Allow-Methods` extends HeaderKey.Default

  object `Access-Control-Allow-Origin` extends HeaderKey.Default

  object `Access-Control-Expose-Headers` extends HeaderKey.Default

  object `Access-Control-Max-Age` extends HeaderKey.Default

  object `Access-Control-Request-Headers` extends HeaderKey.Default

  object `Access-Control-Request-Method` extends HeaderKey.Default

  object Age extends HeaderKey.Default

  object Allow extends HeaderKey.Default

  object Authorization extends HeaderKey.Internal[Authorization] with HeaderKey.Singleton
  final case class Authorization(credentials: Credentials) extends Parsed {
    def key = `Authorization`
    def renderValue[W <: Writer](writer: W): writer.type = credentials.render(writer)
  }

  object `Cache-Control` extends HeaderKey.Internal[`Cache-Control`] with HeaderKey.Recurring
  final case class `Cache-Control`(values: NonEmptyList[CacheDirective]) extends RecurringRenderable {
    def key = `Cache-Control`
    type Value = CacheDirective
  }

  // values should be case insensitive
  //http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
  object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring
  final case class Connection(values: NonEmptyList[CaseInsensitiveString]) extends Recurring {
    def key = Connection
    type Value = CaseInsensitiveString
    def hasClose = values.list.exists(_ == "close".ci)
    def hasKeepAlive = values.list.exists(_ == "keep-alive".ci)
    def renderValue[W <: Writer](writer: W): writer.type = writer.addStrings(values.list.map(_.toString), ", ")
  }

  object `Content-Base` extends HeaderKey.Default

  object `Content-Disposition` extends HeaderKey.Internal[`Content-Disposition`] with HeaderKey.Singleton
  // see http://tools.ietf.org/html/rfc2183
  final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends Parsed {
    def key = `Content-Disposition`
    override lazy val stringValue = super.stringValue
    def renderValue[W <: Writer](writer: W): writer.type = {
      writer.append(dispositionType)
      parameters.foreach(p =>  writer ~ "; "~ p._1~ "=\""~ p._2 ~ '"')
      writer
    }
  }

  object `Content-Encoding` extends HeaderKey.Internal[`Content-Encoding`] with HeaderKey.Singleton
  final case class `Content-Encoding`(contentCoding: ContentCoding) extends Parsed {
    def key = `Content-Encoding`
    def renderValue[W <: Writer](writer: W): writer.type = contentCoding.render(writer)
  }

  object `Content-Language` extends HeaderKey.Default

  object `Content-Length` extends HeaderKey.Internal[`Content-Length`] with HeaderKey.Singleton
  final case class `Content-Length`(length: Int) extends Parsed {
    def key = `Content-Length`
    def renderValue[W <: Writer](writer: W): writer.type = writer.append(length)
  }

  object `Content-Location` extends HeaderKey.Default

  object `Content-Transfer-Encoding` extends HeaderKey.Default

  object `Content-MD5` extends HeaderKey.Default

  object `Content-Range` extends HeaderKey.Default

  object `Content-Type` extends HeaderKey.Internal[`Content-Type`] with HeaderKey.Singleton {
    val `text/plain` = `Content-Type`(MediaType.`text/plain`)
    val `application/octet-stream` = `Content-Type`(MediaType.`application/octet-stream`)

    // RFC4627 defines JSON to always be UTF encoded, we always render JSON to UTF-8
    val `application/json` = `Content-Type`(MediaType.`application/json`, `UTF-8`)

    val `application/xml` = `Content-Type`(MediaType.`application/xml`, `UTF-8`)

    def apply(mediaType: MediaType, charset: Charset): `Content-Type` = apply(mediaType, Some(charset))
    implicit def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)
  }

  final case class `Content-Type`(mediaType: MediaType, definedCharset: Option[Charset]) extends Parsed {
    def key = `Content-Type`
    def renderValue[W <: Writer](writer: W): writer.type = definedCharset match {
      case Some(cs) => writer ~ mediaType ~ "; charset=" ~ cs
      case _        => mediaType.render(writer)
    }

    def withMediaType(mediaType: MediaType) =
      if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
    def withCharset(charset: Charset) =
      if (noCharsetDefined || charset != definedCharset.get) copy(definedCharset = Some(charset)) else this
    def withoutDefinedCharset =
      if (isCharsetDefined) copy(definedCharset = None) else this

    def isCharsetDefined = definedCharset.isDefined
    def noCharsetDefined = definedCharset.isEmpty

    def charset: Charset = definedCharset.getOrElse(`ISO-8859-1`)
  }

  object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring
  final case class Cookie(values: NonEmptyList[org.http4s.Cookie]) extends RecurringRenderable {
    def key = Cookie
    type Value = org.http4s.Cookie
    override def renderValue[W <: Writer](writer: W): writer.type = {
      values.head.render(writer)
      values.tail.foreach( writer ~ "; " ~ _ )
      writer
    }
  }

  object Date extends HeaderKey.Internal[Date] with HeaderKey.Singleton
  final case class Date(date: DateTime) extends Parsed {
    def key = `Date`
    override def stringValue = date.toRfc1123DateTimeString
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(stringValue)
  }

  object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton
  final case class ETag(tag: String) extends Parsed {
    def key: HeaderKey = ETag
    override def stringValue: String = tag
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(tag)
  }

  object Expect extends HeaderKey.Default

  object Expires extends HeaderKey.Default

  object From extends HeaderKey.Default

  object `Front-End-Https` extends HeaderKey.Default

  object Host extends HeaderKey.Internal[Host] with HeaderKey.Singleton {
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }
  final case class Host(host: String, port: Option[Int] = None) extends Parsed {
    def key = `Host`
    def renderValue[W <: Writer](writer: W): writer.type = {
      writer.append(host)
      if (port.isDefined) writer ~ ':' ~ port.get
      writer
    }
  }

  object `If-Match` extends HeaderKey.Default

  object `If-Modified-Since` extends HeaderKey.Internal[`If-Modified-Since`] with HeaderKey.Singleton
  final case class `If-Modified-Since`(date: DateTime) extends Parsed {
    def key: HeaderKey = `If-Modified-Since`
    override def stringValue: String = date.toRfc1123DateTimeString
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(stringValue)
  }

  object `If-None-Match` extends HeaderKey.Internal[`If-None-Match`] with HeaderKey.Singleton
  case class `If-None-Match`(tag: String) extends Parsed {
    def key: HeaderKey = `If-None-Match`
    override def stringValue: String = tag
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(tag)
  }

  object `If-Range` extends HeaderKey.Default

  object `If-Unmodified-Since` extends HeaderKey.Default

  object `Last-Modified` extends HeaderKey.Internal[`Last-Modified`] with HeaderKey.Singleton
  final case class `Last-Modified`(date: DateTime) extends Parsed {
    def key = `Last-Modified`
    override def stringValue = date.toRfc1123DateTimeString
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(stringValue)
  }

  object Location extends HeaderKey.Internal[Location] with HeaderKey.Singleton

  final case class Location(absoluteUri: String) extends Parsed {
    def key = `Location`
    override def stringValue = absoluteUri
    override def renderValue[W <: Writer](writer: W): writer.type = writer.append(absoluteUri)
  }

  object `Max-Forwards` extends HeaderKey.Default

  object Origin extends HeaderKey.Default

  object Pragma extends HeaderKey.Default

  object `Proxy-Authenticate` extends HeaderKey.Default

  object `Proxy-Authorization` extends HeaderKey.Default

  object Range extends HeaderKey.Default

  object Referer extends HeaderKey.Default

  object `Retry-After` extends HeaderKey.Default

  object `Sec-WebSocket-Key` extends HeaderKey.Default

  object `Sec-WebSocket-Key1` extends HeaderKey.Default

  object `Sec-WebSocket-Key2` extends HeaderKey.Default

  object `Sec-WebSocket-Location` extends HeaderKey.Default

  object `Sec-WebSocket-Origin` extends HeaderKey.Default

  object `Sec-WebSocket-Protocol` extends HeaderKey.Default

  object `Sec-WebSocket-Version` extends HeaderKey.Default

  object `Sec-WebSocket-Accept` extends HeaderKey.Default

  object Server extends HeaderKey.Default

  object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] with HeaderKey.Singleton
  final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends Parsed {
    def key = `Set-Cookie`
    def renderValue[W <: Writer](writer: W): writer.type = cookie.render(writer)
  }

  object `TE` extends HeaderKey.Default

  object `Trailer` extends HeaderKey.Default

  object `Transfer-Encoding` extends HeaderKey.Internal[`Transfer-Encoding`] with HeaderKey.Recurring
  final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding]) extends RecurringRenderable {
    def key = `Transfer-Encoding`
    def hasChunked = values.list.exists(_.stringValue.equalsIgnoreCase("chunked"))
    type Value = TransferCoding
  }

  object Upgrade extends HeaderKey.Default

  object `User-Agent` extends HeaderKey.Default

  object Vary extends HeaderKey.Default

  object Via extends HeaderKey.Default

  object Warning extends HeaderKey.Default

  object `WebSocket-Location` extends HeaderKey.Default

  object `WebSocket-Origin` extends HeaderKey.Default

  object `WebSocket-Protocol` extends HeaderKey.Default

  object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring
  final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends RecurringRenderable {
    def key = `WWW-Authenticate`
    type Value = Challenge
  }

  object `X-Forwarded-For` extends HeaderKey.Internal[`X-Forwarded-For`] with HeaderKey.Recurring
  final case class `X-Forwarded-For`(values: NonEmptyList[Option[InetAddress]]) extends Recurring {
    def key = `X-Forwarded-For`
    type Value = Option[InetAddress]
    override lazy val stringValue = super.stringValue
    def renderValue[W <: Writer](writer: W): writer.type = {
      values.head.fold(writer.append("unknown"))(i => writer.append(i.getHostAddress))
      values.tail.foreach(append(writer, _))
      writer
    }

    @inline
    private def append(sb: Writer, add: Option[InetAddress]): Unit = {
      sb.append(", ")
      if (add.isDefined) sb.append(add.get.getHostAddress)
      else sb.append("unknown")
    }
  }

  object `X-Forwarded-Proto` extends HeaderKey.Default

  object `X-Powered-By` extends HeaderKey.Default
}