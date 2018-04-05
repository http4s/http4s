/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/MediaType.scala
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

import cats.{Eq, Order, Show}
import cats.implicits._
import org.http4s.headers.MediaRangeAndQValue
import org.http4s.internal.parboiled2.{Parser => PbParser, _}
import org.http4s.parser.{Http4sParser, Rfc2616BasicRules}
import org.http4s.util.{StringWriter, Writer}
import scala.util.hashing.MurmurHash3

sealed class MediaRange private[http4s] (
    val mainType: String,
    val extensions: Map[String, String] = Map.empty) {

  /** Does that mediaRange satisfy this ranges requirements */
  def satisfiedBy(mediaType: MediaRange): Boolean =
    (mainType.charAt(0) === '*' || mainType === mediaType.mainType)

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

  override def equals(obj: Any): Boolean = obj match {
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
    if (hash == 0) {
      hash = MurmurHash3.mixLast(mainType.toLowerCase.##, extensions.##)
    }
    hash
  }

}

private[http4s] trait MediaParser extends Rfc2616BasicRules { self: PbParser =>
  def MediaRangeRule[A](builder: (String, String) => A): Rule1[A] = rule {
    (("*/*" ~ push("*") ~ push("*")) |
      (Token ~ "/" ~ (("*" ~ push("*")) | Token)) |
      ("*" ~ push("*") ~ push("*"))) ~> (builder(_, _))
  }

  def MediaTypeExtension: Rule1[(String, String)] = rule {
    ";" ~ OptWS ~ Token ~ optional("=" ~ (Token | QuotedString)) ~> {
      (s: String, s2: Option[String]) =>
        (s, s2.getOrElse(""))
    }
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

  /**
    * Parse a MediaRange
    */
  def parse(s: String): ParseResult[MediaRange] =
    new Http4sParser[MediaRange](s, "Invalid Media Range") with MediaRangeParser {
      def main = MediaRangeFull
    }.parse

  private[http4s] def renderExtensions(sb: Writer, mr: MediaRange): Unit =
    mr.extensions.foreach { case (k, v) => sb << ';' << ' ' << k << '=' <<# v }

  private[http4s] def extensionsToString(mr: MediaRange): String = {
    val sw = new StringWriter()
    renderExtensions(sw, mr)
    sw.result
  }

  private[http4s] trait MediaRangeParser extends MediaParser { self: PbParser =>
    def MediaRangeFull: Rule1[MediaRange] = rule {
      MediaRangeDef ~ optional(oneOrMore(MediaTypeExtension)) ~> {
        (mr: MediaRange, ext: Option[Seq[(String, String)]]) =>
          ext.fold(mr)(ex => mr.withExtensions(ex.toMap))
      }
    }

    def MediaRangeDef: Rule1[MediaRange] = MediaRangeRule[MediaRange](getMediaRange)

    private def getMediaRange(mainType: String, subType: String): MediaRange =
      if (subType === "*")
        MediaRange.standard.getOrElse(mainType.toLowerCase, new MediaRange(mainType))
      else
        MediaType.all.getOrElse(
          (mainType.toLowerCase, subType.toLowerCase),
          new MediaType(mainType.toLowerCase, subType.toLowerCase))
  }

  implicit val http4sInstancesForMediaRange
    : Show[MediaRange] with HttpCodec[MediaRange] with Order[MediaRange] =
    new Show[MediaRange] with HttpCodec[MediaRange] with Order[MediaRange] {
      override def show(s: MediaRange): String =
        s"${s.mainType}/*${MediaRange.extensionsToString(s)}"

      override def parse(s: String): ParseResult[MediaRange] =
        MediaRange.parse(s)

      override def render(writer: Writer, mr: MediaRange): writer.type = mr match {
        case mt: MediaType => MediaType.http4sInstancesForMediaType.render(writer, mt)
        case _ =>
          writer << mr.mainType << "/*"
          renderExtensions(writer, mr)
          writer
      }

      override def compare(x: MediaRange, y: MediaRange): Int = {
        def orderedSubtype(a: MediaRange) = a match {
          case mt: MediaType => mt.subType
          case _ => ""
        }
        def f(a: MediaRange) = (a.mainType, orderedSubtype(a), a.extensions.toVector.sortBy(_._1))
        Order[(String, String, Vector[(String, String)])].compare(f(x), f(y))
      }
    }
}

sealed class MediaType(
    mainType: String,
    val subType: String,
    val compressible: Boolean = false,
    val binary: Boolean = false,
    val fileExtensions: Seq[String] = Nil,
    extensions: Map[String, String] = Map.empty)
    extends MediaRange(mainType, extensions) {

  override def withExtensions(ext: Map[String, String]): MediaType =
    new MediaType(mainType, subType, compressible, binary, fileExtensions, ext)

  final def satisfies(mediaType: MediaType): Boolean = mediaType.satisfiedBy(this)

  override def satisfiedBy(mediaType: MediaRange): Boolean = mediaType match {
    case mediaType: MediaType =>
      (this eq mediaType) ||
        mainType === mediaType.mainType &&
          subType === mediaType.subType

    case _ => false
  }

  override def equals(obj: Any): Boolean = obj match {
    case x: MediaType =>
      (this eq x) ||
        mainType === x.mainType &&
          subType === x.subType &&
          extensions === x.extensions
    case _ => false
  }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0) {
      hash = MurmurHash3.mixLast(
        mainType.##,
        MurmurHash3.mix(
          subType.##,
          MurmurHash3.mix(
            compressible.##,
            MurmurHash3.mix(binary.##, MurmurHash3.mix(fileExtensions.##, extensions.##)))))
    }
    hash
  }

  override def toString: String =
    s"MediaType($mainType/$subType${MediaRange.extensionsToString(this)})"
}

object MediaType {
  def forExtension(ext: String): Option[MediaType] = extensionMap.get(ext.toLowerCase)

  def multipart(subType: String, boundary: Option[String] = None): MediaType = {
    val ext = boundary.map(b => Map("boundary" -> b)).getOrElse(Map.empty)
    new MediaType(
      "multipart",
      subType,
      MimeDB.Compressible,
      MimeDB.NotBinary,
      Nil,
      extensions = ext)
  }

  /////////////////////////// PREDEFINED MEDIA-TYPE DEFINITION ////////////////////////////
  // Copied from the definitions on MimeDB
  val `application/javascript`: MediaType = new MediaType(
    "application",
    "javascript",
    MimeDB.Compressible,
    MimeDB.NotBinary,
    List("js", "mjs"))
  val `application/json`: MediaType =
    new MediaType("application", "json", MimeDB.Compressible, MimeDB.Binary, List("json", "map"))
  val `application/octet-stream`: MediaType = new MediaType(
    "application",
    "octet-stream",
    MimeDB.Uncompressible,
    MimeDB.Binary,
    List(
      "bin",
      "dms",
      "lrf",
      "mar",
      "so",
      "dist",
      "distz",
      "pkg",
      "bpk",
      "dump",
      "elc",
      "deploy",
      "exe",
      "dll",
      "deb",
      "dmg",
      "iso",
      "img",
      "msi",
      "msp",
      "msm",
      "buffer")
  )
  val `application/xml`: MediaType = new MediaType(
    "application",
    "xml",
    MimeDB.Compressible,
    MimeDB.NotBinary,
    List("xml", "xsl", "xsd", "rng"))
  val `application/x-www-form-urlencoded`: MediaType =
    new MediaType("application", "x-www-form-urlencoded", MimeDB.Compressible, MimeDB.NotBinary)
  lazy val `audio/ogg`: MediaType =
    new MediaType("audio", "ogg", MimeDB.Uncompressible, MimeDB.Binary, List("oga", "ogg", "spx"))
  val `image/png`: MediaType =
    new MediaType("image", "png", MimeDB.Uncompressible, MimeDB.Binary, List("png"))
  val `text/html`: MediaType = new MediaType(
    "text",
    "html",
    MimeDB.Compressible,
    MimeDB.NotBinary,
    List("html", "htm", "shtml"))
  val `text/plain`: MediaType = new MediaType(
    "text",
    "plain",
    MimeDB.Compressible,
    MimeDB.NotBinary,
    List("txt", "text", "conf", "def", "list", "log", "in", "ini"))
  val `text/xml`: MediaType =
    new MediaType("text", "xml", MimeDB.Compressible, MimeDB.NotBinary, List("xml"))
  lazy val `video/ogg`: MediaType =
    new MediaType("video", "ogg", MimeDB.Uncompressible, MimeDB.Binary, List("ogv"))

  // Curiously text/event-stream isn't included in MimeDB
  val `text/event-stream` = new MediaType("text", "event-stream")
  // nor hal+json
  val `application/hal+json` =
    new MediaType("application", "hal+json", MimeDB.Compressible, MimeDB.Binary)

  val all: Map[(String, String), MediaType] =
    (`application/hal+json` :: `text/event-stream` :: MimeDB.all).map {
      case m => (m.mainType.toLowerCase, m.subType.toLowerCase) -> m
    }.toMap
  val extensionMap: Map[String, MediaType] = MimeDB.all.flatMap {
    case m => m.fileExtensions.map(_ -> m)
  }.toMap

  /**
    * Parse a MediaType
    */
  def parse(s: String): ParseResult[MediaType] =
    new Http4sParser[MediaType](s, "Invalid Media Type") with MediaTypeParser {
      def main = MediaTypeFull
    }.parse

  private[http4s] trait MediaTypeParser extends MediaParser {
    def MediaTypeFull: Rule1[MediaType] = rule {
      MediaTypeDef ~ optional(oneOrMore(MediaTypeExtension)) ~> {
        (mr: MediaType, ext: Option[Seq[(String, String)]]) =>
          ext.fold(mr)(ex => mr.withExtensions(ex.toMap))
      }
    }

    def MediaTypeDef: Rule1[MediaType] = MediaRangeRule[MediaType](getMediaType)

    private def getMediaType(mainType: String, subType: String): MediaType =
      MediaType.all.getOrElse(
        (mainType.toLowerCase, subType.toLowerCase),
        new MediaType(mainType.toLowerCase, subType.toLowerCase))
  }

  implicit val http4sInstancesForMediaType
    : Show[MediaType] with HttpCodec[MediaType] with Eq[MediaType] =
    new Show[MediaType] with HttpCodec[MediaType] with Eq[MediaType] {
      override def show(s: MediaType): String =
        s"${s.mainType}/${s.subType}${MediaRange.extensionsToString(s)}"

      override def parse(s: String): ParseResult[MediaType] =
        MediaType.parse(s)

      override def render(writer: Writer, mt: MediaType): writer.type = {
        writer << mt.mainType << '/' << mt.subType
        MediaRange.renderExtensions(writer, mt)
        writer
      }

      override def eqv(x: MediaType, y: MediaType): Boolean =
        x.equals(y)
    }

}
