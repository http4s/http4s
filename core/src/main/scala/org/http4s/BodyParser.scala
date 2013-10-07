package org.http4s

import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}
import scalaz.\/
import scalaz.stream._
import scalaz.syntax.either._
import scalaz.concurrent.Task

class BodyParser[A] private (p: Process[Task, Response \/ A]) {
  def apply(f: A => Response): Process[Task, Response] = p.map(_.fold(identity, f))
  def map[B](f: A => B): BodyParser[B] = new BodyParser(p.map(_.map(f)))
//  def flatMap[B](f: A => BodyParser[B]): BodyParser[B] =
//    BodyParser(it.flatMap[Either[Response, B]](_.fold(
//      { responder: Response => Done(Left(responder)) },
//      { a: A => f(a).it }
//    )))
//  def joinRight[A1 >: A, B](implicit ev: <:<[A1, Either[Response, B]]): BodyParser[B] = BodyParser(it.map(_.joinRight))
}

object BodyParser {
  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")
/*
  private val BodyChunkConsumer: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume[BodyChunk]()

  implicit def bodyParserToResponderIteratee(bodyParser: BodyParser[Response]): Iteratee[HttpChunk, Response] =
    bodyParser(identity)
*/

  def text[A](req: Request, limit: Int = DefaultMaxEntitySize): BodyParser[String] =
    new BodyParser(req.body |> takeBytes(limit)).map(_.decodeString(req.prelude.charset))
/*
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
*/

  private def takeBytes(n: Int): Process.Process1[HttpChunk, Response \/ HttpChunk] = {
    Process.await1[HttpChunk] flatMap {
      case chunk: BodyChunk =>
        if (chunk.length > n)
          Process.halt
        else
          Process.emit(chunk.right) fby takeBytes(n - chunk.length)
      case chunk =>
        Process.emit(chunk.right) fby takeBytes(n)
    }
  }

/*
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
*/
}
