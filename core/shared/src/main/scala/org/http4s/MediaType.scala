/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/MediaType.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import cats.implicits.{catsSyntaxEither => _, _}
import cats.parse.Parser
import cats.{Eq, Order, Show}
import org.http4s.headers.MediaRangeAndQValue
import org.http4s.util.{StringWriter, Writer}
import org.typelevel.ci._

import scala.util.hashing.MurmurHash3

sealed class MediaRange private[http4s] (
    val mainType: CIString,
    val extensions: List[(CIString, String)] = List.empty) {

  /** Does that mediaRange satisfy this ranges requirements */
  def satisfiedBy(mediaType: MediaRange): Boolean =
    mainType.toString.charAt(0) === '*' || mainType === mediaType.mainType

  final def satisfies(mediaRange: MediaRange): Boolean = mediaRange.satisfiedBy(this)

  def isApplication: Boolean = mainType === ci"application"
  def isAudio: Boolean = mainType === ci"audio"
  def isImage: Boolean = mainType === ci"image"
  def isMessage: Boolean = mainType === ci"message"
  def isMultipart: Boolean = mainType === ci"multipart"
  def isText: Boolean = mainType === ci"text"
  def isVideo: Boolean = mainType === ci"video"

  def withQValue(q: QValue): MediaRangeAndQValue = MediaRangeAndQValue(this, q)

  def withExtensions(ext: List[(CIString, String)]): MediaRange = new MediaRange(mainType, ext)

  override def toString: String = s"MediaRange($mainType/*${MediaRange.extensionsToString(this)})"

  override def equals(obj: Any): Boolean =
    obj match {
      case _: MediaType => false
      case x: MediaRange =>
        (this eq x) ||
          mainType === x.mainType &&
          extensions === x.extensions
      case _ =>
        false
    }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0)
      hash = MurmurHash3.mixLast(mainType.##, extensions.##)
    hash
  }
}

object MediaRange {
  val `*/*` = new MediaRange(ci"*")
  val `application/*` = new MediaRange(ci"application")
  val `audio/*` = new MediaRange(ci"audio")
  val `image/*` = new MediaRange(ci"image")
  val `message/*` = new MediaRange(ci"message")
  val `multipart/*` = new MediaRange(ci"multipart")
  val `text/*` = new MediaRange(ci"text")
  val `video/*` = new MediaRange(ci"video")

  val standard: Map[CIString, MediaRange] =
    List(
      `*/*`,
      `application/*`,
      `audio/*`,
      `image/*`,
      `message/*`,
      `multipart/*`,
      `text/*`,
      `video/*`).map(x => (x.mainType, x)).toMap

  /** Parse a MediaRange
    */
  def parse(s: String): ParseResult[MediaRange] =
    ParseResult.fromParser(fullParser, "media range")(s)

  private[http4s] val mediaTypeExtensionParser: Parser[(CIString, String)] = {
    import Parser.char
    import org.http4s.internal.parsing.Rfc7230.{ows, quotedString, token}

    val escapedString = "\\\\"
    val unescapedString = "\\"

    (char(';') *> ows *> token ~ (char('=') *> token.orElse(quotedString)).?).map {
      case (s: String, s2: Option[String]) =>
        (CIString(s), s2.map(_.replace(escapedString, unescapedString)).getOrElse(""))
    }
  }

  private[http4s] val parser: Parser[MediaRange] = mediaRangeParser(getMediaRange)

  private[http4s] val fullParser: Parser[MediaRange] = {
    val extensions = MediaRange.mediaTypeExtensionParser.rep0

    (parser ~ extensions).map { case (mr, exts) =>
      exts match {
        case Nil => mr
        case _ => mr.withExtensions(exts)
      }
    }
  }

  private[http4s] def renderExtensions(sb: Writer, mr: MediaRange): Unit =
    mr.extensions.foreach { case (k, v) => sb << ';' << ' ' << k << '=' <<# v }

  private[http4s] def extensionsToString(mr: MediaRange): String = {
    val sw = new StringWriter()
    renderExtensions(sw, mr)
    sw.result
  }

  private[http4s] def mediaRangeParser[A](builder: (String, String) => A): Parser[A] = {
    import Parser.string
    import org.http4s.internal.parsing.Rfc7230.token

    val anyStr1 = string("*")

    string("*/*")
      .as(("*", "*"))
      .orElse(
        (token <* string("/")) ~ anyStr1.as("*").orElse(token)
      )
      .orElse(
        anyStr1.as(("*", "*"))
      )
      .map { case (s1: String, s2: String) =>
        builder(s1, s2)
      }
  }

  private[http4s] def getMediaRange(mainType: String, subType: String): MediaRange = {
    val mainCI = CIString(mainType)
    val subCI = CIString(subType)

    if (subCI === ci"*")
      MediaRange.standard.getOrElse(mainCI, new MediaRange(mainCI))
    else
      MediaType.all.getOrElse((mainCI, subCI), new MediaType(mainCI, subCI))
  }

  implicit val http4sShowForMediaRange: Show[MediaRange] =
    Show.show(s => s"${s.mainType}/*${MediaRange.extensionsToString(s)}")
  implicit val http4sOrderForMediaRange: Order[MediaRange] =
    Order.from { (x, y) =>
      def orderedSubtype(a: MediaRange) =
        a match {
          case mt: MediaType => mt.subType
          case _ => ci""
        }
      def f(a: MediaRange) = (a.mainType, orderedSubtype(a), a.extensions.toVector.sortBy(_._1))
      Order[(CIString, CIString, Vector[(CIString, String)])].compare(f(x), f(y))
    }
  implicit val http4sHttpCodecForMediaRange: HttpCodec[MediaRange] =
    new HttpCodec[MediaRange] {
      override def parse(s: String): ParseResult[MediaRange] =
        MediaRange.parse(s)

      override def render(writer: Writer, mr: MediaRange): writer.type =
        mr match {
          case mt: MediaType => MediaType.http4sHttpCodecForMediaType.render(writer, mt)
          case _ =>
            writer << mr.mainType << "/*"
            renderExtensions(writer, mr)
            writer
        }
    }
}

sealed class MediaType(
    mainType: CIString,
    val subType: CIString,
    val compressible: Boolean = false,
    val binary: Boolean = false,
    val fileExtensions: List[String] = Nil,
    extensions: List[(CIString, String)] = List.empty)
    extends MediaRange(mainType, extensions) {
  override def withExtensions(ext: List[(CIString, String)]): MediaType =
    new MediaType(mainType, subType, compressible, binary, fileExtensions, ext)

  final def satisfies(mediaType: MediaType): Boolean = mediaType.satisfiedBy(this)

  override def satisfiedBy(mediaType: MediaRange): Boolean =
    mediaType match {
      case mediaType: MediaType =>
        (this eq mediaType) ||
          mainType === mediaType.mainType &&
          subType === mediaType.subType

      case _ => false
    }

  override def equals(obj: Any): Boolean =
    obj match {
      case x: MediaType =>
        (this eq x) ||
          mainType === x.mainType &&
          subType === x.subType &&
          extensions === x.extensions
      case _ => false
    }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0)
      hash = MurmurHash3.mixLast(
        mainType.##,
        MurmurHash3.mix(
          subType.##,
          MurmurHash3.mix(
            compressible.##,
            MurmurHash3.mix(binary.##, MurmurHash3.mix(fileExtensions.##, extensions.##)))))
    hash
  }

  override def toString: String =
    s"MediaType($mainType/$subType${MediaRange.extensionsToString(this)})"
}

object MediaType extends MimeDB {
  def forExtension(ext: String): Option[MediaType] = extensionMap.get(ext.toLowerCase)

  def multipartType(subType: CIString, boundary: Option[String] = None): MediaType = {
    val ext = boundary.tupleLeft(ci"boundary").toList
    new MediaType(ci"multipart", subType, Compressible, NotBinary, Nil, extensions = ext)
  }

  // Curiously text/event-stream isn't included in MimeDB
  lazy val `text/event-stream` = new MediaType(ci"text", ci"event-stream")

  lazy val all: Map[(CIString, CIString), MediaType] =
    (`text/event-stream` :: allMediaTypes)
      .map(m => (m.mainType, m.subType) -> m)
      .toMap

  val extensionMap: Map[String, MediaType] =
    allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap

  val parser: Parser[MediaType] = {
    val mediaType = MediaRange.mediaRangeParser(getMediaType)
    val extensions = MediaRange.mediaTypeExtensionParser.rep0

    (mediaType ~ extensions).map { case (mr, exts) =>
      exts match {
        case Nil => mr
        case _ => mr.withExtensions(exts)
      }
    }
  }

  /** Parse a MediaType
    */
  def parse(s: String): ParseResult[MediaType] =
    ParseResult.fromParser(parser, "media type")(s)

  /** Parse a MediaType
    *
    * For totality, call [[#parse]]. For compile-time
    * verification of literals, call [[#mediaType]].
    */
  def unsafeParse(s: String): MediaType =
    parse(s).fold(throw _, identity)

  private[http4s] def getMediaType(mainType: String, subType: String): MediaType =
    MediaType.all.getOrElse(
      (CIString(mainType), CIString(subType)),
      new MediaType(CIString(mainType), CIString(subType)))

  implicit val http4sEqForMediaType: Eq[MediaType] =
    Eq.fromUniversalEquals
  implicit val http4sShowForMediaType: Show[MediaType] =
    Show.show(s => s"${s.mainType}/${s.subType}${MediaRange.extensionsToString(s)}")
  implicit val http4sHttpCodecForMediaType: HttpCodec[MediaType] =
    new HttpCodec[MediaType] {
      override def parse(s: String): ParseResult[MediaType] =
        MediaType.parse(s)

      override def render(writer: Writer, mt: MediaType): writer.type = {
        writer << mt.mainType << '/' << mt.subType
        MediaRange.renderExtensions(writer, mt)
        writer
      }
    }
}
