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
import cats.parse.{Parser, Parser1}
import cats.{Eq, Order, Show}
import org.http4s.headers.MediaRangeAndQValue
import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{StringWriter, Writer}
import scala.util.hashing.MurmurHash3

sealed class MediaRange private[http4s] (
    val mainType: String,
    val extensions: Map[String, String] = Map.empty) {

  /** Does that mediaRange satisfy this ranges requirements */
  def satisfiedBy(mediaType: MediaRange): Boolean =
    mainType.charAt(0) === '*' || mainType === mediaType.mainType

  final def satisfies(mediaRange: MediaRange): Boolean = mediaRange.satisfiedBy(this)

  def isApplication: Boolean = mainType === "application"
  def isAudio: Boolean = mainType === "audio"
  def isImage: Boolean = mainType === "image"
  def isMessage: Boolean = mainType === "message"
  def isMultipart: Boolean = mainType === "multipart"
  def isText: Boolean = mainType === "text"
  def isVideo: Boolean = mainType === "video"

  def withQValue(q: QValue): MediaRangeAndQValue = MediaRangeAndQValue(this, q)

  def withExtensions(ext: Map[String, String]): MediaRange = new MediaRange(mainType, ext)

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
      hash = MurmurHash3.mixLast(mainType.toLowerCase.##, extensions.##)
    hash
  }
}

object MediaRange {
  val `*/*` = new MediaRange("*")
  val `application/*` = new MediaRange("application")
  val `audio/*` = new MediaRange("audio")
  val `image/*` = new MediaRange("image")
  val `message/*` = new MediaRange("message")
  val `multipart/*` = new MediaRange("multipart")
  val `text/*` = new MediaRange("text")
  val `video/*` = new MediaRange("video")

  val standard: Map[String, MediaRange] =
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

  private[http4s] val parser: Parser1[MediaRange] = mediaRangeParser(getMediaRange)

  private[http4s] val fullParser: Parser1[MediaRange] = {
    val extension = Parser.rep1(MediaType.mediaTypeExtension, 1).?

    (parser ~ extension).map { case (mr, ext) =>
      ext.fold(mr)(ex => mr.withExtensions(ex.toList.toMap))
    }
  }

  private[http4s] def renderExtensions(sb: Writer, mr: MediaRange): Unit =
    mr.extensions.foreach { case (k, v) => sb << ';' << ' ' << k << '=' <<# v }

  private[http4s] def extensionsToString(mr: MediaRange): String = {
    val sw = new StringWriter()
    renderExtensions(sw, mr)
    sw.result
  }

  private[http4s] def mediaRangeParser[A](builder: (String, String) => A): Parser1[A] = {
    import Parser.string1
    import org.http4s.internal.parsing.Rfc7230.token

    val anyStr1 = string1("*")

    string1("*/*")
      .as(("*", "*"))
      .orElse1(
        (token <* string1("/")) ~ anyStr1.as("*").orElse1(token)
      )
      .orElse1(
        anyStr1.as(("*", "*"))
      )
      .map { case (s1: String, s2: String) =>
        builder(s1, s2)
      }
  }

  private[http4s] def getMediaRange(mainType: String, subType: String): MediaRange =
    if (subType === "*")
      MediaRange.standard.getOrElse(mainType.toLowerCase, new MediaRange(mainType))
    else
      MediaType.all.getOrElse(
        (mainType.toLowerCase, subType.toLowerCase),
        new MediaType(mainType.toLowerCase, subType.toLowerCase))

  implicit val http4sShowForMediaRange: Show[MediaRange] =
    Show.show(s => s"${s.mainType}/*${MediaRange.extensionsToString(s)}")
  implicit val http4sOrderForMediaRange: Order[MediaRange] =
    Order.from { (x, y) =>
      def orderedSubtype(a: MediaRange) =
        a match {
          case mt: MediaType => mt.subType
          case _ => ""
        }
      def f(a: MediaRange) = (a.mainType, orderedSubtype(a), a.extensions.toVector.sortBy(_._1))
      Order[(String, String, Vector[(String, String)])].compare(f(x), f(y))
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
    mainType: String,
    val subType: String,
    val compressible: Boolean = false,
    val binary: Boolean = false,
    val fileExtensions: List[String] = Nil,
    extensions: Map[String, String] = Map.empty)
    extends MediaRange(mainType, extensions) {
  override def withExtensions(ext: Map[String, String]): MediaType =
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

object MediaType extends MimeDB with internal.MediaTypeDeprecated {
  def forExtension(ext: String): Option[MediaType] = extensionMap.get(ext.toLowerCase)

  def multipartType(subType: String, boundary: Option[String] = None): MediaType = {
    val ext = boundary.map(b => Map("boundary" -> b)).getOrElse(Map.empty)
    new MediaType("multipart", subType, Compressible, NotBinary, Nil, extensions = ext)
  }

  // Curiously text/event-stream isn't included in MimeDB
  lazy val `text/event-stream` = new MediaType("text", "event-stream")

  lazy val all: Map[(String, String), MediaType] =
    (`text/event-stream` :: allMediaTypes)
      .map(m => (m.mainType.toLowerCase, m.subType.toLowerCase) -> m)
      .toMap

  val extensionMap: Map[String, MediaType] =
    allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap

  val parser: Parser1[MediaType] = {
    val mediaType = MediaRange.mediaRangeParser(getMediaType)
    val extension = Parser.rep1(mediaTypeExtension, 1).?

    (mediaType ~ extension).map { case (mr, ext) =>
      ext.fold(mr)(ex => mr.withExtensions(ex.toList.toMap))
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
      (mainType.toLowerCase, subType.toLowerCase),
      new MediaType(mainType.toLowerCase, subType.toLowerCase))

  private[http4s] def mediaTypeExtension: Parser1[(String, String)] = {
    import Parser.char
    import Rfc2616BasicRules.optWs
    import org.http4s.internal.parsing.Rfc7230.{quotedString, token}

    val escapedString = "\\\\"
    val unescapedString = "\\"

    (char(';') *> optWs *> token ~ (char('=') *> token.orElse(quotedString)).?).map {
      case (s: String, s2: Option[String]) =>
        (s, s2.map(_.replace(escapedString, unescapedString)).getOrElse(""))
    }
  }

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

  @deprecated("Use org.http4s.syntax.literals._", "<DOTTY>")
  class Macros(override val c: DeprecatedMacrosParameter)
      extends internal.DeprecatedMacros.MediaType(c)
}
