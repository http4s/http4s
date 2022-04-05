package org.http4s.build

import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream
import io.circe._
import io.circe.generic.semiauto._
import java.io.File
import java.io.PrintWriter
import org.http4s.implicits._
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder
import sbt._
import sbt.Keys._
import scala.concurrent.ExecutionContext.global
import scala.io.Source
import treehugger.forest._
import treehugger.forest.definitions._
import treehuggerDSL._

object MimeLoaderPlugin extends AutoPlugin {
  object autoImport {
    val generateMimeDb = taskKey[Unit]("Regenerate MimeDB.scala from the IANA registry")
  }
  import autoImport._

  override def trigger = noTrigger

  override lazy val projectSettings = Seq(
    generateMimeDb := {
      MimeLoader
        .toFile(
          new File(
            baseDirectory.value / ".." / "shared" / "src" / "main" / "scala" / "org" / "http4s",
            "MimeDB.scala",
          ),
          "org.http4s",
          "MimeDB",
          "MediaType",
        )
        .unsafeRunSync()(cats.effect.unsafe.implicits.global)
    }
  )
}

/** MimeLoader is able to generate a scala file with a database of MediaTypes.
  * The list of MediaTypes is produced from the list published by `mime-db` in json format.
  * This json file is parsed, converted to a list of MediaTypes grouped by main type and
  * converted to a Treehugger syntax tree which is later printed as a scala source file
  */
object MimeLoader {
  implicit val MimeDescrDecoder: Decoder[MimeDescr] = deriveDecoder[MimeDescr]
  val url =
    uri"https://raw.githubusercontent.com/jshttp/mime-db/v1.48.0/db.json"
  // Due to the limits on the jvm class size (64k) we cannot put all instances in one object
  // This particularly affects `application` which needs to be divided in 2
  val maxSizePerSection = 500
  def readMimeDB: Stream[IO, List[Mime]] =
    for {
      client <- Stream.resource(EmberClientBuilder.default[IO].build)
      _ <- Stream.eval(IO(println(s"Downloading mimedb from $url")))
      value <- Stream.eval(client.expect[Json](url))
      obj <- Stream.eval(IO(value.arrayOrObject(JsonObject.empty, _ => JsonObject.empty, identity)))
    } yield obj.toMap
      .map(x => (x._1.split("/").toList, x._2))
      .collect { case (m :: s :: Nil, d) =>
        d.as[MimeDescr] match {
          case Right(md) => Some(Mime(m, s, md))
          case Left(_) => None
        }
      }
      .collect { case Some(x) =>
        x
      }
      .toList
      .sortBy(m => (m.mainType, m.secondaryType))

  /** This method will generate trees to produce code for a mime mime type set
    */
  def toTree(mainType: String, objectName: String, mediaTypeClassName: String)(
      mimes: List[Mime]
  ): (List[Tree], String) = {
    def subObject(
        partial: Boolean,
        listVal: String,
        objectName: String,
        mimes: List[Mime],
    ): Tree = {
      val _listVal = s"_$listVal"
      val all: List[Tree] = mkThreadUnsafeLazyVal(
        listVal,
        ListClass.TYPE_OF(TYPE_REF(REF(mediaTypeClassName))),
        LIST(mimes.map(m => REF(m.valName))),
      )
      val mediaTypeClass = RootClass.newClass(mediaTypeClassName)
      val vals: List[Tree] = mimes.map(_.toTree(mediaTypeClass))
      val allVals = vals ++ all
      (if (partial) TRAITDEF(objectName) else OBJECTDEF(objectName)) := BLOCK(allVals)
    }

    if (mimes.length <= maxSizePerSection) {
      (List(subObject(false, "all", objectName, mimes)), objectName)
    } else {
      val subObjects: List[(Tree, String)] = mimes
        .sliding(maxSizePerSection, maxSizePerSection)
        .zipWithIndex
        .map { case (mimes, i) =>
          val mimeObjectName = s"${objectName}_$i"
          (subObject(true, s"part_$i", mimeObjectName, mimes), mimeObjectName)
        }
        .toList
      val reducedAll =
        subObjects.zipWithIndex.map { case (_, i) => REF(s"part_$i") }.foldLeft(NIL) { (a, b) =>
          a.SEQ_++(b)
        }
      val all: List[Tree] =
        mkThreadUnsafeLazyVal(
          "all",
          ListClass.TYPE_OF(TYPE_REF(REF(mediaTypeClassName))),
          reducedAll,
        )
      val objectPartsDefinition = OBJECTDEF(s"${objectName}_parts")
        .withFlags(PRIVATEWITHIN("http4s")) := BLOCK(subObjects.map(_._1))
      val objectDefinition = OBJECTDEF(objectName).withParents(
        subObjects.map(x => s"${objectName}_parts.${x._2}")
      ) := BLOCK(all)
      (List(objectPartsDefinition, objectDefinition), mainType)
    }
  }

  /** A so-called @threadUnsafe lazy "val" using a null-initialized var */
  def mkThreadUnsafeLazyVal(name: String, tpe: Type, value: Tree): List[Tree] = {
    val _name = s"_$name"
    List(
      VAR(_name, tpe).withFlags(Flags.PRIVATE) := NULL,
      DEF(name, tpe) := BLOCK(
        List(
          IF(REF(_name).OBJ_EQ(NULL))
            .THEN(REF(_name) := value)
            .ELSE(UNIT),
          REF(_name),
        )
      ),
    )
  }

  /** Takes all the main type generated trees and put them together on the final tree
    */
  def coalesce(
      l: List[(List[Tree], String)],
      topLevelPackge: String,
      objectName: String,
      mediaTypeClassName: String,
  ): Tree = {
    val privateWithin = topLevelPackge.split("\\.").toList.lastOption.getOrElse("this")
    val reducedAll =
      l.sortBy(_._2).reverse.map(m => REF(s"${m._2.replaceAll("-", "_")}.all")).foldLeft(NIL) {
        (a, b) =>
          a.SEQ_++(b)
      }
    val all: List[Tree] =
      mkThreadUnsafeLazyVal(
        "allMediaTypes",
        ListClass.TYPE_OF(TYPE_REF(REF(mediaTypeClassName))),
        reducedAll,
      )
    val compressible: Tree = VAL("Compressible", BooleanClass) := TRUE
    val uncompressible: Tree = VAL("Uncompressible", BooleanClass) := FALSE
    val binary: Tree = VAL("Binary", BooleanClass) := TRUE
    val notBinary: Tree = VAL("NotBinary", BooleanClass) := FALSE

    (TRAITDEF(objectName).withFlags(PRIVATEWITHIN(privateWithin)) := BLOCK(
      all ::: List(compressible, uncompressible, binary, notBinary) ::: l.flatMap(_._1)
    ))
      .inPackage(topLevelPackge)
      .withComment("Autogenerated file, don't edit")
  }

  // All actual file writing happens here
  private def treeToFile(f: File, t: Tree): Unit = {
    // Create the dir if needed
    Option(f.getParentFile).foreach(_.mkdirs())

    // Retain copyright header
    val src = Source.fromFile(f)
    val header = src.getLines.takeWhile(!_.startsWith("//")).mkString("", "\n", "\n")
    src.close()

    val writer = new PrintWriter(f)
    writer.write(header)
    writer.write(treeToString(t))
    writer.close()
  }

  /** This method will dowload the MimeDB and produce a file with generated code for http4s
    */
  def toFile(
      f: File,
      topLevelPackge: String,
      objectName: String,
      mediaTypeClassName: String,
  ): IO[Unit] =
    (for {
      m <- readMimeDB
      t <- Stream.emit(m.groupBy(_.mainType).toList.sortBy(_._1).map { case (t, l) =>
        toTree(t, s"${t.replaceAll("-", "_")}", mediaTypeClassName)(l)
      })
      o <- Stream.emit(coalesce(t, topLevelPackge, objectName, mediaTypeClassName))
      _ <- Stream.emit(treeToFile(f, o))
    } yield ()).compile.drain
}

final case class MimeDescr(extensions: Option[List[String]], compressible: Option[Boolean])
final case class Mime(mainType: String, secondaryType: String, descr: MimeDescr) {
  // Binary is not on MimeDB. we'll use same mnemonics as in http4s before #1770
  def isBinary: Boolean = mainType match {
    case "audio" => true
    case "image" => true
    case "message" => false
    case "text" => false
    case "video" => true
    case "multipart" => false
    case "application" =>
      secondaryType match {
        case "atom+xml" => false
        case "base64" => true
        case "excel" => true
        case "font-woff" => true
        case "gnutar" => true
        case "gzip" => true
        case "hal+json" => true
        case "java-archive" => true
        case "javascript" => false
        case "json" => true
        case "lha" => true
        case "lzx" => true
        case "mspowerpoint" => true
        case "msword" => true
        case "octet-stream" => true
        case "pdf" => true
        case "problem+json" => true
        case "postscript" => true
        case "rss+xml" => false
        case "soap+xml" => false
        case "vnd.api+json" => true
        case "vnd.google-earth.kml+xml" => false
        case "vnd.google-earth.kmz" => true
        case "vnd.ms-fontobject" => true
        case "vnd.oasis.opendocument.chart" => true
        case "vnd.oasis.opendocument.database" => true
        case "vnd.oasis.opendocument.formula" => true
        case "vnd.oasis.opendocument.graphics" => true
        case "vnd.oasis.opendocument.image" => true
        case "vnd.oasis.opendocument.presentation" => true
        case "vnd.oasis.opendocument.spreadsheet" => true
        case "vnd.oasis.opendocument.text" => true
        case "vnd.oasis.opendocument.text-master" => true
        case "vnd.oasis.opendocument.text-web" => true
        case "vnd.openxmlformats-officedocument.presentationml.presentation" => true
        case "vnd.openxmlformats-officedocument.presentationml.slide" => true
        case "vnd.openxmlformats-officedocument.presentationml.slideshow" => true
        case "vnd.openxmlformats-officedocument.presentationml.template" => true
        case "vnd.openxmlformats-officedocument.spreadsheetml.sheet" => true
        case "vnd.openxmlformats-officedocument.spreadsheetml.template" => true
        case "vnd.openxmlformats-officedocument.wordprocessingml.document" => true
        case "vnd.openxmlformats-officedocument.wordprocessingml.template" => true
        case "x-7z-compressed" => true
        case "x-ace-compressed" => true
        case "x-apple-diskimage" => true
        case "x-arc-compressed" => true
        case "x-bzip" => true
        case "x-bzip2" => true
        case "x-chrome-extension" => true
        case "x-compress" => true
        case "x-debian-package" => true
        case "x-dvi" => true
        case "x-font-truetype" => true
        case "x-font-opentype" => true
        case "x-gtar" => true
        case "x-gzip" => true
        case "x-latex" => true
        case "x-rar-compressed" => true
        case "x-redhat-package-manager" => true
        case "x-shockwave-flash" => true
        case "x-tar" => true
        case "x-tex" => true
        case "x-texinfo" => true
        case "x-vrml" => false
        case "x-www-form-urlencoded" => false
        case "x-x509-ca-cert" => true
        case "x-xpinstall" => true
        case "xhtml+xml" => false
        case "xml-dtd" => false
        case "xml" => false
        case "zip" => true
        case _ => false
      }
    case _ => false
  }
  val compressibleRef =
    if (descr.compressible.forall(_ == true)) Mime.CompressibleRef else Mime.UncompressibleRef
  val binaryRef = if (isBinary) Mime.BinaryRef else Mime.NotBinaryRef
  val extensions: Tree =
    descr.extensions.filter(_.nonEmpty).map(x => LIST(x.map(LIT))).getOrElse(NIL)
  val valName: String = s"`$secondaryType`"
  def toTree(mediaTypeClass: ClassSymbol): Tree =
    if (descr.extensions.isEmpty) {
      LAZYVAL(valName, mediaTypeClass) := NEW(
        mediaTypeClass,
        LIT(mainType),
        LIT(secondaryType),
        compressibleRef,
        binaryRef,
      )
    } else {
      LAZYVAL(valName, mediaTypeClass) := NEW(
        mediaTypeClass,
        LIT(mainType),
        LIT(secondaryType),
        compressibleRef,
        binaryRef,
        extensions,
      )
    }
}

object Mime {
  // References to constants
  val CompressibleRef = REF("Compressible")
  val UncompressibleRef = REF("Uncompressible")
  val BinaryRef = REF("Binary")
  val NotBinaryRef = REF("NotBinary")
}
