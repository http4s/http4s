/*
package org.http4s

import scala.language.reflectiveCalls
import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}

case class BodyParser[A](it: Iteratee[HttpChunk, Either[Response, A]]) {
  def apply(f: A => Response): Iteratee[HttpChunk, Response] = it.map(_.right.map(f).merge)
  def map[B](f: A => B): BodyParser[B] = BodyParser(it.map[Either[Response, B]](_.right.map(f)))
  def flatMap[B](f: A => BodyParser[B]): BodyParser[B] =
    BodyParser(it.flatMap[Either[Response, B]](_.fold(
      { responder: Response => Done(Left(responder)) },
      { a: A => f(a).it }
    )))
  def joinRight[A1 >: A, B](implicit ev: <:<[A1, Either[Response, B]]): BodyParser[B] = BodyParser(it.map(_.joinRight))
}

object BodyParser {
  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  private val BodyChunkConsumer: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume[BodyChunk]()

  implicit def bodyParserToResponderIteratee(bodyParser: BodyParser[Response]): Iteratee[HttpChunk, Response] =
    bodyParser(identity)

  def text[A](charset: HttpCharset, limit: Int = DefaultMaxEntitySize): BodyParser[String] =
    consumeUpTo(BodyChunkConsumer, limit).map(_.decodeString(charset))

  /**
   * Handles a request body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param charset the charset of the input
   * @param limit the maximum size before an EntityTooLarge error is returned
   * @param parser the SAX parser to use to parse the XML
   * @return a request handler
   */
  def xml(charset: HttpCharset,
          limit: Int = DefaultMaxEntitySize,
          parser: SAXParser = XML.parser,
          onSaxException: SAXException => Response = { saxEx => /*saxEx.printStackTrace();*/ Status.BadRequest() })
  : BodyParser[Elem] =
    consumeUpTo(BodyChunkConsumer, limit).map { bytes =>
      val in = bytes.iterator.asInputStream
      val source = new InputSource(in)
      source.setEncoding(charset.value)
      Try(XML.loadXML(source, parser)).map(Right(_)).recover {
        case e: SAXException => Left(onSaxException(e))
      }.get
    }.joinRight

  def ignoreBody: BodyParser[Unit] = BodyParser(whileBodyChunk &>> Iteratee.ignore[BodyChunk].map(Right(_)))

  def trailer: BodyParser[TrailerChunk] = BodyParser(
    Enumeratee.dropWhile[HttpChunk](_.isInstanceOf[BodyChunk]) &>>
      (Iteratee.head[HttpChunk].map {
        case Some(trailer: TrailerChunk) => trailer
        case _ => TrailerChunk()
      }.map(Right(_))))

  def consumeUpTo[A](consumer: Iteratee[BodyChunk, A], limit: Int = DefaultMaxEntitySize): BodyParser[A] = {
    val it = for {
      a <- Traversable.takeUpTo[BodyChunk](limit) &>> consumer
      tooLargeOrA <- Iteratee.eofOrElse(Status.RequestEntityTooLarge())(a)
    } yield tooLargeOrA
    BodyParser(whileBodyChunk &>> it)
  }

  val whileBodyChunk: Enumeratee[HttpChunk, BodyChunk] = new CheckDone[HttpChunk, BodyChunk] {
    def step[A](k: K[BodyChunk, A]): K[HttpChunk, Iteratee[BodyChunk, A]] = {
      case in @ Input.El(e: BodyChunk) =>
        new CheckDone[HttpChunk, BodyChunk] {
          def continue[A](k: K[BodyChunk, A]) = Cont(step(k))
        } &> k(in.asInstanceOf[Input[BodyChunk]])
      case in @ Input.El(e) =>
        Done(Cont(k), in)
      case in @ Input.Empty =>
        new CheckDone[HttpChunk, BodyChunk] { def continue[A](k: K[BodyChunk, A]) = Cont(step(k)) } &> k(in)
      case Input.EOF => Done(Cont(k), Input.EOF)
    }
    def continue[A](k: K[BodyChunk, A]) = Cont(step(k))
  }

  // File operations
  def binFile(file: java.io.File)(f: => Response): Iteratee[HttpChunk,Response] = {
    val out = new java.io.FileOutputStream(file)
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => out.write(d.toArray) }.map{ _ => out.close(); f }
  }

  def textFile(req: RequestPrelude, in: java.io.File)(f: => Response): Iteratee[HttpChunk,Response] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => is.print(d.decodeString(req.charset)) }.map{ _ => is.close(); f }
  }
}
*/
