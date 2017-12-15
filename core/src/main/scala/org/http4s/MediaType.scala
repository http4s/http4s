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

import cats.{Order, Show}
import cats.implicits._
import java.util.concurrent.atomic.AtomicReference
import org.http4s.headers.MediaRangeAndQValue
import org.http4s.internal.parboiled2.{Parser => PbParser, _}
import org.http4s.parser.{Http4sParser, Rfc2616BasicRules}
import org.http4s.util.{Renderable, Writer}
import scala.annotation.tailrec

sealed class MediaRange private[http4s] (
    val mainType: String,
    val extensions: Map[String, String] = Map.empty)
    extends Renderable {

  override def render(writer: Writer): writer.type = {
    writer << mainType << "/*"
    renderExtensions(writer)
    writer
  }

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

  override def toString: String = s"MediaRange($renderString)"

  override def equals(obj: Any): Boolean = obj match {
    case _: MediaType => false
    case x: MediaRange =>
      (this eq x) ||
        mainType === x.mainType &&
          extensions === x.extensions
    case _ =>
      false
  }

  override def hashCode(): Int = renderString.##

  private[http4s] def renderExtensions(sb: Writer): Unit = if (extensions.nonEmpty) {
    extensions.foreach { case (k, v) => sb << ';' << ' ' << k << '=' <<# v }
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

  private[http4s] trait MediaRangeParser extends Rfc2616BasicRules { self: PbParser =>
    def MediaRangeFull: Rule1[MediaRange] = rule {
      MediaRangeDef ~ optional(oneOrMore(MediaTypeExtension)) ~> {
        (mr: MediaRange, ext: Option[Seq[(String, String)]]) =>
          ext.fold(mr)(ex => mr.withExtensions(ex.toMap))
      }
    }

    def MediaRangeDef: Rule1[MediaRange] = rule {
      (("*/*" ~ push("*") ~ push("*")) |
        (Token ~ "/" ~ (("*" ~ push("*")) | Token)) |
        ("*" ~ push("*") ~ push("*"))) ~> (getMediaRange(_, _))
    }

    def MediaTypeExtension: Rule1[(String, String)] = rule {
      ";" ~ OptWS ~ Token ~ optional("=" ~ (Token | QuotedString)) ~> {
        (s: String, s2: Option[String]) =>
          (s, s2.getOrElse(""))
      }
    }

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
      override def show(s: MediaRange): String = s.toString

      override def parse(s: String): ParseResult[MediaRange] =
        MediaRange.parse(s)

      override def render(writer: Writer, range: MediaRange): writer.type =
        range.render(writer)

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

  override def render(writer: Writer): writer.type = {
    writer << mainType << '/' << subType
    renderExtensions(writer)
    writer
  }

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

  override def hashCode(): Int = renderString.##
  override def toString: String = s"MediaType($renderString)"
}

object MediaType {
  // TODO error handling
  implicit def fromKey(k: (String, String)): MediaType = new MediaType(k._1, k._2)
  implicit def fromValue(v: MediaType): (String, String) =
    (v.mainType.toLowerCase, v.subType.toLowerCase)

  def unapply(mimeType: MediaType): Option[(String, String)] =
    Some((mimeType.mainType, mimeType.subType))

  private[this] val extensionMap = new AtomicReference(Map.empty[String, MediaType])

  @tailrec
  private def registerFileExtension(ext: String, mediaType: MediaType): Unit = {
    val lcExt = ext.toLowerCase
    val current = extensionMap.get
    require(
      !current.contains(lcExt),
      "Extension '%s' clash: media-types '%s' and '%s'".format(ext, current(lcExt), mediaType))
    val updated = current.updated(lcExt, mediaType)
    if (!extensionMap.compareAndSet(current, updated)) registerFileExtension(ext, mediaType)
  }

  private def register(mediaType: MediaType): MediaType = {
    mediaType.fileExtensions.foreach(registerFileExtension(_, mediaType))
    mediaType
  }

  def forExtension(ext: String): Option[MediaType] = extensionMap.get.get(ext.toLowerCase)

  /////////////////////////// PREDEFINED MEDIA-TYPE DEFINITION ////////////////////////////
  private def compressible = true
  private def uncompressible = false
  private def binary = true
  private def notBinary = false

  def multipart(subType: String, boundary: Option[String] = None): MediaType = {
    val ext = boundary.map(b => Map("boundary" -> b)).getOrElse(Map.empty)
    register(new MediaType("multipart", subType, compressible, notBinary, Nil, extensions = ext))
  }

  private[this] def app(
      subType: String,
      compressible: Boolean,
      binary: Boolean,
      fileExtensions: String*) =
    register(new MediaType("application", subType, compressible, binary, fileExtensions))

  private[this] def aud(subType: String, compressible: Boolean, fileExtensions: String*) =
    register(new MediaType("audio", subType, compressible, binary, fileExtensions))

  private[this] def img(
      subType: String,
      compressible: Boolean,
      binary: Boolean,
      fileExtensions: String*) =
    register(new MediaType("image", subType, compressible, binary, fileExtensions))

  private[this] def msg(subType: String, fileExtensions: String*) =
    register(new MediaType("message", subType, compressible, notBinary, fileExtensions))

  private[this] def txt(subType: String, fileExtensions: String*) =
    register(new MediaType("text", subType, compressible, notBinary, fileExtensions))

  private[this] def vid(subType: String, fileExtensions: String*) =
    register(new MediaType("video", subType, uncompressible, binary, fileExtensions))

  // scalastyle:off line.size.limit
  val `application/atom+xml` = app("atom+xml", compressible, notBinary, "atom")
  val `application/base64` = app("base64", compressible, binary, "mm", "mme")
  val `application/excel` = app(
    "excel",
    uncompressible,
    binary,
    "xl",
    "xla",
    "xlb",
    "xlc",
    "xld",
    "xlk",
    "xll",
    "xlm",
    "xls",
    "xlt",
    "xlv",
    "xlw")
  val `application/font-woff` = app("font-woff", uncompressible, binary, "woff")
  val `application/gnutar` = app("gnutar", uncompressible, binary, "tgz")
  val `application/gzip` = app("gzip", uncompressible, binary, "gz")
  val `application/hal+json` = app("hal+json", compressible, binary) // we treat JSON as binary, since its encoding is not variable but defined by RFC4627
  val `application/java-archive` = app("java-archive", uncompressible, binary, "jar", "war", "ear")
  val `application/javascript` = app("javascript", compressible, notBinary, "js")
  val `application/json` = app("json", compressible, binary, "json") // we treat JSON as binary, since its encoding is not variable but defined by RFC4627
  val `application/lha` = app("lha", uncompressible, binary, "lha")
  val `application/lzx` = app("lzx", uncompressible, binary, "lzx")
  val `application/mspowerpoint` =
    app("mspowerpoint", uncompressible, binary, "pot", "pps", "ppt", "ppz")
  val `application/msword` =
    app("msword", uncompressible, binary, "doc", "dot", "w6w", "wiz", "word", "wri")
  val `application/octet-stream` = app(
    "octet-stream",
    uncompressible,
    binary,
    "a",
    "bin",
    "class",
    "dump",
    "exe",
    "lhx",
    "lzh",
    "o",
    "psd",
    "saveme",
    "zoo")
  val `application/pdf` = app("pdf", uncompressible, binary, "pdf")
  val `application/problem+json` = app("problem+json", compressible, binary) // we treat JSON as binary, since its encoding is not variable but defined by RFC4627
  val `application/postscript` = app("postscript", compressible, binary, "ai", "eps", "ps")
  val `application/rss+xml` = app("rss+xml", compressible, notBinary, "rss")
  val `application/soap+xml` = app("soap+xml", compressible, notBinary)
  val `application/vnd.api+json` = app("vnd.api+json", compressible, binary) // we treat JSON as binary, since its encoding is not variable but defined by RFC4627
  val `application/vnd.google-earth.kml+xml` =
    app("vnd.google-earth.kml+xml", compressible, notBinary, "kml")
  val `application/vnd.google-earth.kmz` =
    app("vnd.google-earth.kmz", uncompressible, binary, "kmz")
  val `application/vnd.ms-fontobject` = app("vnd.ms-fontobject", compressible, binary, "eot")
  val `application/vnd.oasis.opendocument.chart` =
    app("vnd.oasis.opendocument.chart", compressible, binary, "odc")
  val `application/vnd.oasis.opendocument.database` =
    app("vnd.oasis.opendocument.database", compressible, binary, "odb")
  val `application/vnd.oasis.opendocument.formula` =
    app("vnd.oasis.opendocument.formula", compressible, binary, "odf")
  val `application/vnd.oasis.opendocument.graphics` =
    app("vnd.oasis.opendocument.graphics", compressible, binary, "odg")
  val `application/vnd.oasis.opendocument.image` =
    app("vnd.oasis.opendocument.image", compressible, binary, "odi")
  val `application/vnd.oasis.opendocument.presentation` =
    app("vnd.oasis.opendocument.presentation", compressible, binary, "odp")
  val `application/vnd.oasis.opendocument.spreadsheet` =
    app("vnd.oasis.opendocument.spreadsheet", compressible, binary, "ods")
  val `application/vnd.oasis.opendocument.text` =
    app("vnd.oasis.opendocument.text", compressible, binary, "odt")
  val `application/vnd.oasis.opendocument.text-master` =
    app("vnd.oasis.opendocument.text-master", compressible, binary, "odm", "otm")
  val `application/vnd.oasis.opendocument.text-web` =
    app("vnd.oasis.opendocument.text-web", compressible, binary, "oth")
  val `application/vnd.openxmlformats-officedocument.presentationml.presentation` = app(
    "vnd.openxmlformats-officedocument.presentationml.presentation",
    compressible,
    binary,
    "pptx")
  val `application/vnd.openxmlformats-officedocument.presentationml.slide` =
    app("vnd.openxmlformats-officedocument.presentationml.slide", compressible, binary, "sldx")
  val `application/vnd.openxmlformats-officedocument.presentationml.slideshow` =
    app("vnd.openxmlformats-officedocument.presentationml.slideshow", compressible, binary, "ppsx")
  val `application/vnd.openxmlformats-officedocument.presentationml.template` =
    app("vnd.openxmlformats-officedocument.presentationml.template", compressible, binary, "potx")
  val `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` =
    app("vnd.openxmlformats-officedocument.spreadsheetml.sheet", compressible, binary, "xlsx")
  val `application/vnd.openxmlformats-officedocument.spreadsheetml.template` =
    app("vnd.openxmlformats-officedocument.spreadsheetml.template", compressible, binary, "xltx")
  val `application/vnd.openxmlformats-officedocument.wordprocessingml.document` =
    app("vnd.openxmlformats-officedocument.wordprocessingml.document", compressible, binary, "docx")
  val `application/vnd.openxmlformats-officedocument.wordprocessingml.template` =
    app("vnd.openxmlformats-officedocument.wordprocessingml.template", compressible, binary, "dotx")
  val `application/x-7z-compressed` = app("x-7z-compressed", uncompressible, binary, "7z", "s7z")
  val `application/x-ace-compressed` = app("x-ace-compressed", uncompressible, binary, "ace")
  val `application/x-apple-diskimage` = app("x-apple-diskimage", uncompressible, binary, "dmg")
  val `application/x-arc-compressed` = app("x-arc-compressed", uncompressible, binary, "arc")
  val `application/x-bzip` = app("x-bzip", uncompressible, binary, "bz")
  val `application/x-bzip2` = app("x-bzip2", uncompressible, binary, "boz", "bz2")
  val `application/x-chrome-extension` = app("x-chrome-extension", uncompressible, binary, "crx")
  val `application/x-compress` = app("x-compress", uncompressible, binary, "z")
  val `application/x-debian-package` = app("x-debian-package", compressible, binary, "deb")
  val `application/x-dvi` = app("x-dvi", compressible, binary, "dvi")
  val `application/x-font-truetype` = app("x-font-truetype", compressible, binary, "ttf")
  val `application/x-font-opentype` = app("x-font-opentype", compressible, binary, "otf")
  val `application/x-gtar` = app("x-gtar", uncompressible, binary, "gtar")
  val `application/x-gzip` = app("x-gzip", uncompressible, binary, "gzip")
  val `application/x-latex` = app("x-latex", compressible, binary, "latex", "ltx")
  val `application/x-rar-compressed` = app("x-rar-compressed", uncompressible, binary, "rar")
  val `application/x-redhat-package-manager` =
    app("x-redhat-package-manager", uncompressible, binary, "rpm")
  val `application/x-shockwave-flash` = app("x-shockwave-flash", uncompressible, binary, "swf")
  val `application/x-tar` = app("x-tar", compressible, binary, "tar")
  val `application/x-tex` = app("x-tex", compressible, binary, "tex")
  val `application/x-texinfo` = app("x-texinfo", compressible, binary, "texi", "texinfo")
  val `application/x-vrml` = app("x-vrml", compressible, notBinary, "vrml")
  val `application/x-www-form-urlencoded` = app("x-www-form-urlencoded", compressible, notBinary)
  val `application/x-x509-ca-cert` = app("x-x509-ca-cert", compressible, binary, "der")
  val `application/x-xpinstall` = app("x-xpinstall", uncompressible, binary, "xpi")
  val `application/xhtml+xml` = app("xhtml+xml", compressible, notBinary)
  val `application/xml-dtd` = app("xml-dtd", compressible, notBinary)
  val `application/xml` = app("xml", compressible, notBinary)
  val `application/zip` = app("zip", uncompressible, binary, "zip")

  val allApplication: Map[(String, String), MediaType] = List(
    `application/atom+xml`,
    `application/base64`,
    `application/excel`,
    `application/font-woff`,
    `application/gnutar`,
    `application/gzip`,
    `application/hal+json`,
    `application/java-archive`,
    `application/javascript`,
    `application/json`,
    `application/lha`,
    `application/lzx`,
    `application/mspowerpoint`,
    `application/msword`,
    `application/octet-stream`,
    `application/pdf`,
    `application/problem+json`,
    `application/postscript`,
    `application/rss+xml`,
    `application/soap+xml`,
    `application/vnd.api+json`,
    `application/vnd.google-earth.kml+xml`,
    `application/vnd.google-earth.kmz`,
    `application/vnd.ms-fontobject`,
    `application/vnd.oasis.opendocument.chart`,
    `application/vnd.oasis.opendocument.database`,
    `application/vnd.oasis.opendocument.formula`,
    `application/vnd.oasis.opendocument.graphics`,
    `application/vnd.oasis.opendocument.image`,
    `application/vnd.oasis.opendocument.presentation`,
    `application/vnd.oasis.opendocument.spreadsheet`,
    `application/vnd.oasis.opendocument.text`,
    `application/vnd.oasis.opendocument.text-master`,
    `application/vnd.oasis.opendocument.text-web`,
    `application/vnd.openxmlformats-officedocument.presentationml.presentation`,
    `application/vnd.openxmlformats-officedocument.presentationml.slide`,
    `application/vnd.openxmlformats-officedocument.presentationml.slideshow`,
    `application/vnd.openxmlformats-officedocument.presentationml.template`,
    `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
    `application/vnd.openxmlformats-officedocument.spreadsheetml.template`,
    `application/vnd.openxmlformats-officedocument.wordprocessingml.document`,
    `application/vnd.openxmlformats-officedocument.wordprocessingml.template`,
    `application/x-7z-compressed`,
    `application/x-ace-compressed`,
    `application/x-apple-diskimage`,
    `application/x-arc-compressed`,
    `application/x-bzip`,
    `application/x-bzip2`,
    `application/x-chrome-extension`,
    `application/x-compress`,
    `application/x-debian-package`,
    `application/x-dvi`,
    `application/x-font-truetype`,
    `application/x-font-opentype`,
    `application/x-gtar`,
    `application/x-gzip`,
    `application/x-latex`,
    `application/x-rar-compressed`,
    `application/x-redhat-package-manager`,
    `application/x-shockwave-flash`,
    `application/x-tar`,
    `application/x-tex`,
    `application/x-texinfo`,
    `application/x-vrml`,
    `application/x-www-form-urlencoded`,
    `application/x-x509-ca-cert`,
    `application/x-xpinstall`,
    `application/xhtml+xml`,
    `application/xml-dtd`,
    `application/xml`,
    `application/zip`
  ).map(x => (x.mainType, x.subType) -> x).toMap
  // scalastyle:on line.size.limit

  val `audio/aiff` = aud("aiff", compressible, "aif", "aifc", "aiff")
  val `audio/basic` = aud("basic", compressible, "au", "snd")
  val `audio/midi` = aud("midi", compressible, "mid", "midi", "kar")
  val `audio/mod` = aud("mod", uncompressible, "mod")
  val `audio/mpeg` = aud("mpeg", uncompressible, "m2a", "mp2", "mp3", "mpa", "mpga")
  val `audio/ogg` = aud("ogg", uncompressible, "oga", "ogg")
  val `audio/voc` = aud("voc", uncompressible, "voc")
  val `audio/vorbis` = aud("vorbis", uncompressible, "vorbis")
  val `audio/voxware` = aud("voxware", uncompressible, "vox")
  val `audio/wav` = aud("wav", compressible, "wav")
  val `audio/x-realaudio` = aud("x-pn-realaudio", uncompressible, "ra", "ram", "rmm", "rmp")
  val `audio/x-psid` = aud("x-psid", compressible, "sid")
  val `audio/xm` = aud("xm", uncompressible, "xm")
  val allAudio: Map[(String, String), MediaType] = List(
    `audio/aiff`,
    `audio/basic`,
    `audio/midi`,
    `audio/mod`,
    `audio/mpeg`,
    `audio/ogg`,
    `audio/voc`,
    `audio/vorbis`,
    `audio/voxware`,
    `audio/wav`,
    `audio/x-realaudio`,
    `audio/x-psid`,
    `audio/xm`
  ).map(x => (x.mainType, x.subType) -> x).toMap

  val `image/gif` = img("gif", uncompressible, binary, "gif")
  val `image/jpeg` = img("jpeg", uncompressible, binary, "jpe", "jpeg", "jpg")
  val `image/pict` = img("pict", compressible, binary, "pic", "pict")
  val `image/png` = img("png", uncompressible, binary, "png")
  val `image/svg+xml` = img("svg+xml", compressible, notBinary, "svg", "svgz")
  val `image/tiff` = img("tiff", compressible, binary, "tif", "tiff")
  val `image/x-icon` = img("x-icon", compressible, binary, "ico")
  val `image/x-ms-bmp` = img("x-ms-bmp", compressible, binary, "bmp")
  val `image/x-pcx` = img("x-pcx", compressible, binary, "pcx")
  val `image/x-pict` = img("x-pict", compressible, binary, "pct")
  val `image/x-quicktime` = img("x-quicktime", uncompressible, binary, "qif", "qti", "qtif")
  val `image/x-rgb` = img("x-rgb", compressible, binary, "rgb")
  val `image/x-xbitmap` = img("x-xbitmap", compressible, binary, "xbm")
  val `image/x-xpixmap` = img("x-xpixmap", compressible, binary, "xpm")
  val allImage: Map[(String, String), MediaType] = List(
    `image/gif`,
    `image/jpeg`,
    `image/pict`,
    `image/png`,
    `image/svg+xml`,
    `image/tiff`,
    `image/x-icon`,
    `image/x-ms-bmp`,
    `image/x-pcx`,
    `image/x-pict`,
    `image/x-quicktime`,
    `image/x-rgb`,
    `image/x-xbitmap`,
    `image/x-xpixmap`
  ).map(x => (x.mainType, x.subType) -> x).toMap

  val `message/http` = msg("http")
  val `message/delivery-status` = msg("delivery-status")
  val `message/rfc822` = msg("rfc822", "eml", "mht", "mhtml", "mime")
  val allMessage: Map[(String, String), MediaType] = List(
    `message/http`,
    `message/delivery-status`,
    `message/rfc822`).map(x => (x.mainType, x.subType) -> x).toMap

  val `multipart/mixed` = multipart("mixed")
  val `multipart/alternative` = multipart("alternative")
  val `multipart/related` = multipart("related")
  val `multipart/form-data` = multipart("form-data")
  val `multipart/signed` = multipart("signed")
  val `multipart/encrypted` = multipart("encrypted")
  val allMultipart: Map[(String, String), MediaType] = List(
    `multipart/mixed`,
    `multipart/alternative`,
    `multipart/related`,
    `multipart/form-data`,
    `multipart/signed`,
    `multipart/encrypted`).map(x => (x.mainType, x.subType) -> x).toMap

  val `text/asp` = txt("asp", "asp")
  val `text/cache-manifest` = txt("cache-manifest", "manifest")
  val `text/calendar` = txt("calendar", "ics", "icz")
  val `text/css` = txt("css", "css")
  val `text/csv` = txt("csv", "csv")
  val `text/event-stream` = txt("event-stream")
  val `text/html` = txt("html", "htm", "html", "htmls", "htx")
  val `text/mcf` = txt("mcf", "mcf")
  val `text/plain` = txt("plain", "conf", "text", "txt", "properties")
  val `text/richtext` = txt("richtext", "rtf", "rtx")
  val `text/tab-separated-values` = txt("tab-separated-values", "tsv")
  val `text/uri-list` = txt("uri-list", "uni", "unis", "uri", "uris")
  val `text/vnd.wap.wml` = txt("vnd.wap.wml", "wml")
  val `text/vnd.wap.wmlscript` = txt("vnd.wap.wmlscript", "wmls")
  val `text/x-asm` = txt("x-asm", "asm", "s")
  val `text/x-c` = txt("x-c", "c", "cc", "cpp")
  val `text/x-component` = txt("x-component", "htc")
  val `text/x-h` = txt("x-h", "h", "hh")
  val `text/x-java-source` = txt("x-java-source", "jav", "java")
  val `text/x-pascal` = txt("x-pascal", "p")
  val `text/x-script` = txt("x-script", "hlb")
  val `text/x-scriptcsh` = txt("x-scriptcsh", "csh")
  val `text/x-scriptelisp` = txt("x-scriptelisp", "el")
  val `text/x-scriptksh` = txt("x-scriptksh", "ksh")
  val `text/x-scriptlisp` = txt("x-scriptlisp", "lsp")
  val `text/x-scriptperl` = txt("x-scriptperl", "pl")
  val `text/x-scriptperl-module` = txt("x-scriptperl-module", "pm")
  val `text/x-scriptphyton` = txt("x-scriptphyton", "py")
  val `text/x-scriptrexx` = txt("x-scriptrexx", "rexx")
  val `text/x-scriptscheme` = txt("x-scriptscheme", "scm")
  val `text/x-scriptsh` = txt("x-scriptsh", "sh")
  val `text/x-scripttcl` = txt("x-scripttcl", "tcl")
  val `text/x-scripttcsh` = txt("x-scripttcsh", "tcsh")
  val `text/x-scriptzsh` = txt("x-scriptzsh", "zsh")
  val `text/x-server-parsed-html` = txt("x-server-parsed-html", "shtml", "ssi")
  val `text/x-setext` = txt("x-setext", "etx")
  val `text/x-sgml` = txt("x-sgml", "sgm", "sgml")
  val `text/x-speech` = txt("x-speech", "spc", "talk")
  val `text/x-uuencode` = txt("x-uuencode", "uu", "uue")
  val `text/x-vcalendar` = txt("x-vcalendar", "vcs")
  val `text/x-vcard` = txt("x-vcard", "vcf", "vcard")
  val `text/xml` = txt("xml", "xml")
  val allText: Map[(String, String), MediaType] = List(
    `text/asp`,
    `text/cache-manifest`,
    `text/calendar`,
    `text/css`,
    `text/csv`,
    `text/event-stream`,
    `text/html`,
    `text/mcf`,
    `text/plain`,
    `text/richtext`,
    `text/tab-separated-values`,
    `text/uri-list`,
    `text/vnd.wap.wml`,
    `text/vnd.wap.wmlscript`,
    `text/x-asm`,
    `text/x-c`,
    `text/x-component`,
    `text/x-h`,
    `text/x-java-source`,
    `text/x-pascal`,
    `text/x-script`,
    `text/x-scriptcsh`,
    `text/x-scriptelisp`,
    `text/x-scriptksh`,
    `text/x-scriptlisp`,
    `text/x-scriptperl`,
    `text/x-scriptperl-module`,
    `text/x-scriptphyton`,
    `text/x-scriptrexx`,
    `text/x-scriptscheme`,
    `text/x-scriptsh`,
    `text/x-scripttcl`,
    `text/x-scripttcsh`,
    `text/x-scriptzsh`,
    `text/x-server-parsed-html`,
    `text/x-setext`,
    `text/x-sgml`,
    `text/x-speech`,
    `text/x-uuencode`,
    `text/x-vcalendar`,
    `text/x-vcard`,
    `text/xml`
  ).map(x => (x.mainType, x.subType) -> x).toMap

  val `video/avs-video` = vid("avs-video", "avs")
  val `video/divx` = vid("divx", "divx")
  val `video/gl` = vid("gl", "gl")
  val `video/mp4` = vid("mp4", "mp4")
  val `video/mpeg` = vid("mpeg", "m1v", "m2v", "mpe", "mpeg", "mpg")
  val `video/ogg` = vid("ogg", "ogv")
  val `video/quicktime` = vid("quicktime", "moov", "mov", "qt")
  val `video/x-dv` = vid("x-dv", "dif", "dv")
  val `video/x-flv` = vid("x-flv", "flv")
  val `video/x-motion-jpeg` = vid("x-motion-jpeg", "mjpg")
  val `video/x-ms-asf` = vid("x-ms-asf", "asf")
  val `video/x-msvideo` = vid("x-msvideo", "avi")
  val `video/x-sgi-movie` = vid("x-sgi-movie", "movie", "mv")
  val allVideo: Map[(String, String), MediaType] = List(
    `video/avs-video`,
    `video/divx`,
    `video/gl`,
    `video/mp4`,
    `video/mpeg`,
    `video/ogg`,
    `video/quicktime`,
    `video/x-dv`,
    `video/x-flv`,
    `video/x-motion-jpeg`,
    `video/x-ms-asf`,
    `video/x-msvideo`,
    `video/x-sgi-movie`
  ).map(x => (x.mainType, x.subType) -> x).toMap

  val all
    : Map[(String, String), MediaType] = allApplication ++ allAudio ++ allAudio ++ allImage ++ allMessage ++ allMultipart ++ allText ++ allVideo

}
