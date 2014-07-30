package org.http4s

import java.io.{File, FileOutputStream, StringReader}
import javax.xml.parsers.SAXParser

import org.xml.sax.InputSource

import scala.util.control.NonFatal
import scala.xml.{Elem, XML}
import scalaz.concurrent.Task
import scalaz.stream.processes
import scalaz.stream.io

// TODO: Need to handle failure in a more uniform manner

/** Decoder that describes the MediaTypes it can decode */
sealed trait EntityDecoder[+T] { self =>

  final def apply(msg: Message): Task[T] = decode(msg)

  def decode(msg: Message): Task[T]

  def consumes: Set[MediaRange]

  def map[T2](f: T => T2): EntityDecoder[T2] = new EntityDecoder[T2] {
    override def consumes: Set[MediaRange] = self.consumes

    override def decode(msg: Message): Task[T2] = self.decode(msg).map(f)
  }

  def orElse[T2 >: T](other: EntityDecoder[T2]): EntityDecoder[T2] = new EntityDecoder.OrDec(this, other)

  def matchesMediaType(msg: Message): Boolean = {
    if (!consumes.isEmpty) {
      msg.headers.get(Header.`Content-Type`).flatMap {
        h => consumes.find(_.satisfiedBy(h.mediaType))
      }.isDefined
    }
    else false
  }
}

object EntityDecoder extends EntityDecoderInstances {
  def apply[T](f: Message => Task[T], valid: MediaRange*): EntityDecoder[T] = new EntityDecoder[T] {
    override def decode(msg: Message): Task[T] = {
      try f(msg)
      catch { case NonFatal(e) => Task.fail(e) }
    }

    override val consumes: Set[MediaRange] = valid.toSet
  }

  private class OrDec[+T](a: EntityDecoder[T], b: EntityDecoder[T]) extends EntityDecoder[T] {
    override def decode(msg: Message): Task[T] = {
      if (a.matchesMediaType(msg)) a.decode(msg)
      else b.decode(msg)
    }

    override val consumes: Set[MediaRange] = a.consumes ++ b.consumes
  }

  def collectBinary(msg: Message): Task[Array[Byte]] =
    msg.body.runLog.map(_.reduce(_ ++ _).toArray)
}

/** Various instances of common decoders */
trait EntityDecoderInstances {
  import EntityDecoder._

  /////////////////// Instances //////////////////////////////////////////////
  implicit val binary: EntityDecoder[Array[Byte]] = {
    EntityDecoder(collectBinary, MediaRange.`*/*`)
  }

  implicit val text: EntityDecoder[String] = {
    def decodeString(msg: Message): Task[String] = {
      val buff = new StringBuilder
      (msg.body |> processes.fold(buff) { (b, c) => {
        b.append(new String(c.toArray, (msg.charset.charset)))
      }}).map(_.result()).runLastOr("")
    }
    EntityDecoder(msg => collectBinary(msg).map(new String(_, msg.charset.charset)),
      MediaRange.`text/*`)
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
    collectBinary(msg).map { arr =>
      val source = new InputSource(new StringReader(new String(arr, msg.charset.charset)))
      XML.loadXML(source, parser)
    }
  }, MediaType.`text/xml`)

  def xml: EntityDecoder[Elem] = xml()

  // File operations
  // TODO: rewrite these using NIO non blocking FileChannels, and do these make sense as a 'decoder'?
  def binFile(file: File): EntityDecoder[File] = {
    EntityDecoder(msg => {
      val p = io.chunkW(new java.io.FileOutputStream(file))
      msg.body.to(p).run.map(_ => file)
    }, MediaRange.`*/*`)
  }

  def textFile(in: java.io.File): EntityDecoder[File] = {
    EntityDecoder(msg => {
      val p = io.chunkW(new java.io.PrintStream(new FileOutputStream(in)))
      msg.body.to(p).run.map(_ => in)
    }, MediaRange.`text/*`)
  }
}
