package org.http4s

import java.io.{File, FileOutputStream, StringReader}
import javax.xml.parsers.SAXParser

import org.http4s.EntityDecoder.DecodingException
import org.http4s.util.ReplyException
import org.xml.sax.{SAXException, SAXParseException, InputSource}
import scodec.bits.ByteVector

import scala.util.control.{NoStackTrace, NonFatal}
import scala.xml.{Elem, XML}
import scalaz.{EitherT, \/}
import scalaz.concurrent.Task
import scalaz.stream.{io, process1}
import scalaz.syntax.monad._

import util.UrlFormCodec.{ decode => formDecode }
import util.ByteVectorInstances.byteVectorMonoidInstance


/** A type that can be used to decode an [[EntityBody]]
  * EntityDecoder is used to attempt to decode an [[EntityBody]] returning the
  * entire resulting T. If an error occurs it will result in a failed Task
  * These are not streaming constructs.
  * @tparam T result type produced by the decoder
  */
sealed trait EntityDecoder[T] { self =>

  final def apply(msg: Message): Task[T] = decode(msg).valueOr(throw _)

  def decode(msg: Message): EitherT[Task, DecodingException, T]

  def consumes: Set[MediaRange]

  def map[T2](f: T => T2): EntityDecoder[T2] = new EntityDecoder[T2] {
    override def consumes: Set[MediaRange] = self.consumes

    override def decode(msg: Message): EitherT[Task, DecodingException, T2] = self.decode(msg).map(f)
  }

//  def orElse[T2 >: T](other: EntityDecoder[T2]): EntityDecoder[T2] = new EntityDecoder.OrDec(this, other)

  def matchesMediaType(msg: Message): Boolean = {
    if (consumes.nonEmpty) {
      msg.headers.get(Header.`Content-Type`) match {
        case Some(h) => matchesMediaType(h.mediaType)
        case None    => false
      }
    }
    else false
  }

  def matchesMediaType(mediaType: MediaType): Boolean = consumes.nonEmpty && {
    consumes.exists(_.satisfiedBy(mediaType))
  }
}

/** EntityDecoder is used to attempt to decode an [[EntityBody]]
  * This companion object provides a way to create `new EntityDecoder`s along
  * with some commonly used instances which can be resolved implicitly.
  */
object EntityDecoder extends EntityDecoderInstances {

  case class DecodingException(reason: String, cause: Option[Throwable] = None)
    extends Exception(reason, cause.orNull)
    with ReplyException
    with NoStackTrace
  {
    override def asResponse(version: HttpVersion): Response =
      ResponseBuilder(Status.BadRequest, version, reason).run
  }

  def apply[T](f: Message => EitherT[Task, DecodingException, T], valid: MediaRange*): EntityDecoder[T] = new EntityDecoder[T] {
    override def decode(msg: Message): EitherT[Task, DecodingException, T] = {
      try f(msg)
      catch {
        case NonFatal(e) => EitherT[Task, DecodingException, T](Task.fail(e))
      }
    }

    override val consumes: Set[MediaRange] = valid.toSet
  }

  private class OrDec[T](a: EntityDecoder[T], b: EntityDecoder[T]) extends EntityDecoder[T] {
    override def decode(msg: Message): EitherT[Task, DecodingException, T] = {
      if (a.matchesMediaType(msg)) a.decode(msg)
      else b.decode(msg)
    }

    override val consumes: Set[MediaRange] = a.consumes ++ b.consumes
  }

  /** Helper method which simply gathers the body into a single ByteVector */
  def collectBinary(msg: Message): EitherT[Task, DecodingException, ByteVector] =
    EitherT.right(msg.body.runFoldMap(identity))

  /** Decodes a message to a String */
  def decodeString(msg: Message): Task[String] = {
    val buff = new StringBuilder
    (msg.body |> process1.fold(buff) { (b, c) => {
      b.append(new String(c.toArray, msg.charset.nioCharset))
    }}).map(_.result()).runLastOr("")
  }
}

/** Implementations of the EntityDecoder instances */
trait EntityDecoderInstances {
  import EntityDecoder._

  /////////////////// Instances //////////////////////////////////////////////

  /** Provides a mechanism to fail decoding */
  def error[T](t: Throwable) = new EntityDecoder[T] {
    override def decode(msg: Message): EitherT[Task, DecodingException, T] = {
      msg.body.kill.run
      EitherT[Task, DecodingException, T](Task.fail(t))
    }
    override def consumes: Set[MediaRange] = Set.empty
  }

  implicit val binary: EntityDecoder[ByteVector] = {
    EntityDecoder(collectBinary, MediaRange.`*/*`)
  }

  implicit val text: EntityDecoder[String] = {
    EntityDecoder(msg => collectBinary(msg).map(bs => new String(bs.toArray, msg.charset.nioCharset)),
      MediaRange.`text/*`)
  }

  // application/x-www-form-urlencoded
  implicit val formEncoded: EntityDecoder[Map[String, Seq[String]]] = {
    val fn = decodeString(_: Message).flatMap { s =>
      Task.now(formDecode(s).leftMap(f => {
        val msg = "Invalid Form Encoding. Expected format: " + f.details
        DecodingException(msg)
      }))
    }

    EntityDecoder(fn.andThen(EitherT.eitherT), MediaType.`application/x-www-form-urlencoded`)
  }

  /**
   * Handles a message body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param parser the SAX parser to use to parse the XML
   * @return an XML element
   */
  implicit def xml(implicit parser: SAXParser = XML.parser): EntityDecoder[Elem] = EntityDecoder(msg => {
    collectBinary(msg).flatMap { arr =>
      val source = new InputSource(new StringReader(new String(arr.toArray, msg.charset.nioCharset)))
      try EitherT.right(Task.now(XML.loadXML(source, parser)))
      catch {
        case e: SAXParseException =>
          val msg = s"${e.getMessage}; Line: ${e.getLineNumber}; Column: ${e.getColumnNumber}"
          EitherT.left(Task.now(DecodingException(msg)))

        case e: SAXException =>
          EitherT.left(Task.now(DecodingException("Invalid XML: " + e.getMessage )))

        case NonFatal(e) => EitherT[Task, DecodingException, Elem](Task.fail(e))
      }
    }
  }, MediaType.`text/xml`)

  def xml: EntityDecoder[Elem] = xml()

  // File operations
  // TODO: rewrite these using NIO non blocking FileChannels, and do these make sense as a 'decoder'?
  def binFile(file: File): EntityDecoder[File] = {
    EntityDecoder(msg => {
      val p = io.chunkW(new java.io.FileOutputStream(file))
      EitherT.right(msg.body.to(p).run).map(_ => file)
    }, MediaRange.`*/*`)
  }

  def textFile(in: java.io.File): EntityDecoder[File] = {
    EntityDecoder(msg => {
      val p = io.chunkW(new java.io.PrintStream(new FileOutputStream(in)))
      EitherT.right(msg.body.to(p).run).map(_ => in)
    }, MediaRange.`text/*`)
  }
}
