package org.http4s

import scala.language.reflectiveCalls
import play.api.libs.iteratee._
import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}
import play.api.libs.iteratee.Enumeratee.CheckDone
import util.Execution.{overflowingExecutionContext => oec, trampoline => tec}
import scala.concurrent.ExecutionContext

case class BodyParser[A](it: Iteratee[Chunk, Either[Response, A]]) {
  def apply(f: A => Response): Iteratee[Chunk, Response] = it.map(_.right.map(f).merge)(oec)
  def map[B](f: A => B)(implicit ec: ExecutionContext): BodyParser[B] = BodyParser(it.map[Either[Response, B]](_.right.map(f)))
  def flatMap[B](f: A => BodyParser[B])(implicit ec: ExecutionContext): BodyParser[B] =
    BodyParser(it.flatMap[Either[Response, B]](_.fold(
      { response: Response => Done(Left(response)) },
      { a: A => f(a).it }
    )))

  def joinRight[A1 >: A, B](implicit ev: <:<[A1, Either[Response, B]]): BodyParser[B] = BodyParser(it.map(_.joinRight)(oec))
}

object BodyParser {
  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  private val BodyChunkConsumer: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume[BodyChunk]()

  implicit def bodyParserToResponderIteratee(bodyParser: BodyParser[Response]): Iteratee[Chunk, Response] =
    bodyParser(identity)

  def text[A](charset: CharacterSet, limit: Int = DefaultMaxEntitySize): BodyParser[String] =
    consumeUpTo(BodyChunkConsumer, limit).map(_.decodeString(charset))(oec)

  /**
   * Handles a request body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   * TODO how to pass the EC correctly?
   *
   * @param charset the charset of the input
   * @param limit the maximum size before an EntityTooLarge error is returned
   * @param parser the SAX parser to use to parse the XML
   * @return a request handler
   */
  def xml(charset: CharacterSet,
          limit: Int = DefaultMaxEntitySize,
          parser: SAXParser = XML.parser,
          onSaxException: SAXException => Response = { saxEx => /*saxEx.printStackTrace();*/ Status.BadRequest() })
  : BodyParser[Elem] =
    consumeUpTo(BodyChunkConsumer, limit).map { bytes =>
      val in = bytes.iterator.asInputStream
      val source = new InputSource(in)
      source.setEncoding(charset.name)
      Try(XML.loadXML(source, parser)).map(Right(_)).recover {
        case e: SAXException => Left(onSaxException(e))
      }.get
    }(tec).joinRight

  def ignoreBody: BodyParser[Unit] = BodyParser(whileBodyChunk &>> Iteratee.ignore[BodyChunk].map(Right(_))(oec))

  def trailer: BodyParser[TrailerChunk] = BodyParser(
    Enumeratee.dropWhile[Chunk](_.isInstanceOf[BodyChunk])(oec) &>>
      (Iteratee.head[Chunk].map {
        case Some(trailer: TrailerChunk) => Right(trailer)
        case _ =>                           Right(TrailerChunk())
      }(oec)))

  def consumeUpTo[A](consumer: Iteratee[BodyChunk, A], limit: Int = DefaultMaxEntitySize): BodyParser[A] = {
    implicit val ec = tec   // for ec requirements in the for comprehension
    val it = for {
      a <- Traversable.takeUpTo[BodyChunk](limit) &>> consumer
      tooLargeOrA <- Iteratee.eofOrElse(Status.RequestEntityTooLarge())(a)
    } yield tooLargeOrA
    BodyParser(whileBodyChunk &>> it)
  }

  val whileBodyChunk: Enumeratee[Chunk, BodyChunk] = new CheckDone[Chunk, BodyChunk] {
    def step[A](k: K[BodyChunk, A]): K[Chunk, Iteratee[BodyChunk, A]] = {
      case in @ Input.El(e: BodyChunk) =>
        new CheckDone[Chunk, BodyChunk] {
          def continue[A](k: K[BodyChunk, A]) = Cont(step(k))
        } &> k(in.asInstanceOf[Input[BodyChunk]])
      case in @ Input.El(e) =>
        Done(Cont(k), in)
      case in @ Input.Empty =>
        new CheckDone[Chunk, BodyChunk] { def continue[A](k: K[BodyChunk, A]) = Cont(step(k)) } &> k(in)
      case Input.EOF => Done(Cont(k), Input.EOF)
    }
    def continue[A](k: K[BodyChunk, A]) = Cont(step(k))
  }

  // TODO: why are we using blocking file ops here!?!
  // File operations
  def binFile(file: java.io.File)(f: => Response)(implicit ec: ExecutionContext): Iteratee[Chunk,Response] = {
    val out = new java.io.FileOutputStream(file)
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => out.write(d.toArray) }(ec).map{ _ => out.close(); f }(oec)
  }

  def textFile(req: RequestPrelude, in: java.io.File)(f: => Response)(implicit ec: ExecutionContext): Iteratee[Chunk,Response] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    whileBodyChunk &>> Iteratee.foreach[BodyChunk]{ d => is.print(d.decodeString(req.charset)) }(ec).map{ _ => is.close(); f }(oec)
  }
}
