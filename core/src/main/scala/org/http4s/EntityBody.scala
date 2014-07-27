package org.http4s

import java.io._

import xml.{Elem, XML}
import org.xml.sax.InputSource
import javax.xml.parsers.SAXParser
import scalaz.stream.processes
import scalaz.concurrent.Task
import scalaz.stream.Process._

object EntityBody extends EntityBodyFunctions {
  val empty: EntityBody = halt
}

trait EntityBodyFunctions {

  def text[A](req: Request): Task[String] = {
    val buff = new StringBuilder
    (req.body |> processes.fold(buff) { (b, c) => {
      b.append(new String(c.toArray, (req.charset.charset)))
    }}).map(_.result()).runLastOr("")
  }

  /**
   * Handles a request body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param parser the SAX parser to use to parse the XML
   * @return a request handler
   */
  def xml(req: Request, parser: SAXParser = XML.parser): Task[Elem] =
    text(req).map { s =>
    // TODO: exceptions here should be handled by Task, but are not until 7.0.5+
      val source = new InputSource(new StringReader(s))
      XML.loadXML(source, parser)
    }

  // File operations
  // TODO: rewrite these using NIO non blocking FileChannels
  def binFile(req: Request, file: java.io.File)(f: => Task[Response]) = {
    val out = new java.io.FileOutputStream(file)
    req.body
      .map{c => out.write(c.toArray) }
      .run.flatMap{_ => out.close(); f}
  }

  def textFile(req: Request, in: java.io.File)(f: => Task[Response]): Task[Response] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    req.body
      .map{ d => is.print(new String(d.toArray, req.charset.charset)) }
      .run.flatMap{_ => is.close(); f}
  }
}

