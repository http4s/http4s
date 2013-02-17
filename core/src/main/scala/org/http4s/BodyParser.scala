package org.http4s

import scala.language.reflectiveCalls
import play.api.libs.iteratee._
import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}

object BodyParser {
  // TODO make configurable
  val DefaultMaxSize = 2 * 1024 * 1024
  private val RawConsumer: Iteratee[Raw, Raw] = Iteratee.consume[Raw]()

  def text(request: RequestPrelude, limit: Int = DefaultMaxSize)(f: String => Responder): Iteratee[HttpChunk, Responder] =
    consumeUpTo(RawConsumer, limit) { raw => f(new String(raw, request.charset)) }

  /**
   * Handles a request body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param request the request prelude
   * @param limit the maximum size before an EntityTooLarge error is returned
   * @param parser the SAX parser to use to parse the XML
   * @param f a function of the XML body to the responder
   * @return a request handler
   */
  def xml(request: RequestPrelude,
          limit: Int = DefaultMaxSize,
          parser: SAXParser = XML.parser,
          onSaxException: SAXException => Responder = { saxEx => saxEx.printStackTrace(); Status.BadRequest() })
         (f: Elem => Responder): Iteratee[HttpChunk, Responder] =
    consumeUpTo(RawConsumer, limit) { raw =>
      val in = new ByteArrayInputStream(raw)
      val source = new InputSource(in)
      source.setEncoding(request.charset.name)
      Try(XML.loadXML(source, parser)).map(f).recover {
        case e: SAXException => onSaxException(e)
      }.get
    }

  def consumeUpTo[A](consumer: Iteratee[Raw, A], limit: Int)(f: A => Responder): Iteratee[HttpChunk, Responder] =
    Enumeratee.map[HttpChunk](_.bytes) &>> (for {
      raw <- Traversable.takeUpTo[Raw](limit) &>> consumer
      tooLargeOrRaw <- Iteratee.eofOrElse(Status.RequestEntityTooLarge())(raw)
    } yield (tooLargeOrRaw.right.map(f).merge))

  // File operations
  def binFile(in: java.io.File)(f: => Responder): Iteratee[HttpChunk,Responder] = {
    val is = new java.io.FileOutputStream(in)
    Iteratee.foreach[HttpChunk]{d=>is.write(d.bytes)}.map{_ => is.close(); f }
  }

  def textFile(req: RequestPrelude, in: java.io.File)(f: => Responder): Iteratee[HttpChunk,Responder] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    Iteratee.foreach[HttpChunk]{ d => is.print(new String(d.bytes, req.charset))}.map{ _ => is.close(); f }
  }
}
