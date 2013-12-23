package org.http4s

import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Failure, Success, Try}
import scalaz.{\/-, -\/, \/}
import scalaz.stream.{processes, process1}
import scalaz.syntax.id._
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.{NonFatal, NoStackTrace}
import com.typesafe.scalalogging.slf4j.Logging
import scalaz.std.string._

object BodyParser {
  case class EntityTooLarge(limit: Int) extends Exception with NoStackTrace
  case object NoParseResult extends Exception with NoStackTrace

  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  def text[A](req: Request, limit: Int = DefaultMaxEntitySize): Task[String] = {
    val buff = new StringBuilder
    (req.body |> takeBytes(limit) |> processes.fold(buff) { (b, c) => c match {
      case c: BodyChunk => b.append(c.decodeString(req.charset))
      case _ => b
    }}).map(_.result()).runLastOr("")
  }



  /**
   * Handles a request body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param limit the maximum size before an EntityTooLarge error is returned
   * @param parser the SAX parser to use to parse the XML
   * @return a request handler
   */
  def xml(req: Request,
          limit: Int = DefaultMaxEntitySize,
          parser: SAXParser = XML.parser): Task[Elem] =
    text(req, limit).flatMap { s =>
      val source = new InputSource(new StringReader(s))
      try Task.now(XML.loadXML(source, parser))
      catch { case t: Throwable => Task.fail(t) }
    }

  private def takeBytes(n: Int): Process1[Chunk, Chunk] = {
    def go(taken: Int, chunk: Chunk): Process1[Chunk, Chunk] = chunk match {
      case c: BodyChunk =>
        val sz = taken + c.length
        if (sz > n) fail(EntityTooLarge(n))
        else Emit(c::Nil, await(Get[Chunk])(go(sz, _)))

      case c =>  Emit(c::Nil, await(Get[Chunk])(go(taken, _)))
    }
    await(Get[Chunk])(go(0,_))
  }

  def comsumeUpTo(n: Int): Process1[Chunk, BodyChunk] = {
    val p = process1.fold[Chunk, BodyChunk](BodyChunk())((c1, c2) => c2 match {
      case c2: BodyChunk => c1 ++ (c2)
      case _ => c1
    })
    takeBytes(n) |> p
  }

  def whileBodyChunk: Process1[Chunk, BodyChunk] = {
    def go(chunk: Chunk): Process1[Chunk, BodyChunk] = chunk match {
      case c: BodyChunk => Emit(c::Nil, await(Get[Chunk])(go))
      case _ => halt
    }
    await(Get[Chunk])(go)
  }

  // File operations
  // TODO: rewrite these using NIO non blocking FileChannels
  def binFile(req: Request, file: java.io.File)(f: => Task[Response]) = {
    val out = new java.io.FileOutputStream(file)
    req.body.pipe(whileBodyChunk)
      .map{c => out.write(c.toArray) }
      .run.flatMap{_ => out.close(); f}
  }

  def textFile(req: Request, in: java.io.File)(f: => Task[Response]): Task[Response] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    req.body.pipe(whileBodyChunk)
      .map{ d => is.print(d.decodeString(req.charset)) }
      .run.flatMap{_ => is.close(); f}
  }
}
