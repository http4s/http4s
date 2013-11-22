package org.http4s

import java.io._
import xml.{Elem, XML, NodeSeq}
import org.xml.sax.{SAXException, InputSource}
import javax.xml.parsers.SAXParser
import scala.util.{Success, Try}
import scalaz.{\/-, -\/, \/}
import scalaz.stream.process1
import scalaz.syntax.id._
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scala.collection.mutable.ArrayBuffer


/** ParserFailHandler generates a response from a failed parser.
  * Type parametrized for implicit convenience.
 *
 * @tparam A The result type of the parser
 */
trait ParserFailHandler[-A] { parent =>
  def handle(t: Throwable): Task[Response]

  // Just gives the response for this parser, but available for overriding if the case dictates
  private[http4s] def mutate[B](other: ParserFailHandler[B]): ParserFailHandler[B] = new ParserFailHandler[B] {
    def handle(t: Throwable): Task[Response] = parent.handle(t)
  }
}

object ParserFailHandler {
  implicit object generalFailure extends ParserFailHandler[Any] {
    def handle(t: Throwable): Task[Response] = Status.InternalServerError()
  }
}

class BodyParser[A] private (p: Process1[Chunk, A], req: Request) { parent =>
  def apply(f: A => Task[Response])(implicit fail: ParserFailHandler[A]): Task[Response] = {
    req.body.pipe(p).runLast.attempt.flatMap {
      case \/-(Some(a)) => f(a)
      case -\/(t)       => fail.handle(t)
      case _            => Status.InternalServerError()
    }
  }

  def andThen[B](p2: Process1[A, B]) = new BodyParser[B](p.pipe(p2), req)

  def map[B](m: A => B)(implicit first: ParserFailHandler[A]): BodyParser[B] = new BodyParser(p.map(m), req) {
    override def apply(f: (B) => Task[Response])(implicit fail: ParserFailHandler[B]) = {
      parent.apply(m.andThen(f))(fail.mutate(first))
    }
  }
}

object BodyParser {

  val DefaultMaxEntitySize = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  //  private val BodyChunkConsumer: Process1[BodyChunk, BodyChunk] =
  //    process1.scan[BodyChunk,StringBuilder](new StringBuilder)((b, c) => c.toArray)

  //  implicit def bodyParserToResponderIteratee(bodyParser: BodyParser[Response]): Iteratee[Chunk, Response] =
  //    bodyParser(identity)

  def text[A](req: Request, charset: CharacterSet = CharacterSet.`UTF-8`, limit: Int = DefaultMaxEntitySize) = {
      //  (f: String => Task[Response], fail: (Throwable, Task[Response]) => Task[Response] = (_, r) => r)
    
    val buff = new StringBuilder
    val p = process1.fold[Chunk, StringBuilder](buff){(b,c) =>
      c match {
        case c: BodyChunk => b.append(c.decodeString(charset))
        case _ =>
      }
      b
    }.map(_.result())
    new BodyParser(takeBytes(limit).pipe(p), req)
  }



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
  def xml(req: Request,
          charset: CharacterSet = CharacterSet.`UTF-8`,
          limit: Int = DefaultMaxEntitySize,
          parser: SAXParser = XML.parser) //,
          //onSaxException: SAXException => Response = { saxEx => /*saxEx.printStackTrace();*/ Status.BadRequest() })
  : BodyParser[Elem] = {
    val p = comsumeUpTo(limit).flatMap{ chunk =>
      val source = new InputSource(chunk.asInputStream)
      source.setEncoding(charset.value)
      try emit(XML.loadXML(source, parser))
      catch { case e: Throwable => Halt(e)
      }
    }
    new BodyParser(p, req)
  }

  implicit object XMLParserErrorHandler extends ParserFailHandler[Elem] {
    def handle(t: Throwable): Task[Response] = t match {
      case e: SAXException => Status.BadRequest()
      case e => Status.InternalServerError()
    }
  }

  /*

  def ignoreBody: BodyParser[Unit] = BodyParser(whileBodyChunk &>> Iteratee.ignore[BodyChunk].map(Right(_))(oec))

  def trailer: BodyParser[TrailerChunk] = BodyParser(
    Enumeratee.dropWhile[Chunk](_.isInstanceOf[BodyChunk])(oec) &>>
      (Iteratee.head[Chunk].map {
        case Some(trailer: TrailerChunk) => Right(trailer)
        case _ =>                           Right(TrailerChunk())
      }(oec)))
*/

  private def takeBytes(n: Int): Process1[Chunk, Chunk] = {
    def go(taken: Int, chunk: Chunk): Process1[Chunk, Chunk] = chunk match {
      case c: BodyChunk =>
        val sz = taken + c.length
        if (sz > n) Halt(new Exception(s"Body Parse size exceeded: $sz > $n"))
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

/*
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
*/
}
