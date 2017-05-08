package org.http4s
package multipart

import scodec.bits.ByteVector
import fs2._
import cats.implicits._
import fs2.util.syntax._
import fs2.Chunk

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {


  private[this] val logger = org.log4s.getLogger

  private val CRLF = "\r\n"
  private val DASHDASH = "--"
  private val startLine: Boundary => String = boundary => s"$DASHDASH${boundary.value}"
  private val endLine: Boundary => String = boundary => s"${startLine(boundary)}$DASHDASH"
  private val expected: Boundary => String = boundary => s"$CRLF${startLine(boundary)}"

  private val CRLFBytes = ByteVector('\r','\n')
//  private val DashDashBytes = ByteVector('-', '-')
//  private val boundaryBytes : Boundary => ByteVector = boundary => ByteVector(boundary.value.getBytes)
//  private val startLineBytes : Boundary => ByteVector = boundaryBytes andThen (DashDashBytes ++ _)
//  private val endLineBytes: Boundary => ByteVector = startLineBytes andThen (_ ++ DashDashBytes)
//  private val expectedBytes: Boundary => ByteVector = startLineBytes andThen (CRLF ++ _)

  final case class Out[+A](a: A, tail: Option[ByteVector] = None)

  def parse(boundary: Boundary, headerLimit: Long = 40 * 1024): Pipe[Task, Byte, Either[Headers, Byte]] = s => {

    val streamString = s.chunks
      .through(text.utf8DecodeC)
      .covary[Task]
      .through(text.lines)
      .dropWhile(_ != startLine(boundary)) // Drop Prelude
      .drop(1)                             // Drop Start Line
      .takeWhile(_ != endLine(boundary))   // Take Until EndLine

      val headers =
        streamString
          .takeWhile(_ != "")
          .map(header)
          .unNone
          .fold(Headers.empty){(acc, h) => acc.put(h)}
          .map(Either.left)

    val initBody = streamString
        .dropWhile(_ != "")
        .drop(1)
        .takeWhile(_ != endLine(boundary))

    val bodySansLast = initBody.dropLast.map(_ ++ CRLF)
    val trimLast = initBody.last.unNone

    val body = (bodySansLast ++ trimLast)
        .through(text.utf8Encode)
        .map(Either.right)


    val autoFail =
      streamString
        .forall(_.length <= headerLimit)
        .flatMap{ b =>
          if (b) streamString.exists(_ != endLine(boundary))
          else Stream.fail(MalformedMessageBodyFailure(s"Part header was longer than ${headerLimit}-byte limit"))
        }

    autoFail.flatMap{ bool =>
      if (bool) headers ++ body
      else Stream.fail(MalformedMessageBodyFailure(s"Expected a multipart start or end line"))
    }

  }

  val header : String => Option[Header.Raw] = line => {
    val idx = line indexOf ':'
    if (idx >= 0 && idx < line.length - 1) Some(Header(line.substring(0, idx), line.substring(idx + 1).trim))
    else None
  }


  val chunkToBV : Chunk[Byte] => ByteVector = chunk =>{
    val bytes = chunk.toBytes
    ByteVector(bytes.values, bytes.offset, bytes.size)
  }

  val bvToChunk: ByteVector => Chunk[Byte] = bv => Chunk.bytes(bv.toArray)

  def receiveLine[F[_]](leading: Option[ByteVector])
                       (h: Handle[F, ByteVector]): Pull[F,Either[ByteVector, Out[ByteVector]], Unit] = {
    def handle(
                bv: ByteVector,
                h: Handle[F, ByteVector]
              ): Pull[F, Either[ByteVector, Out[ByteVector]], Unit] = {

      logger.error(s"handle: ${bv.decodeUtf8}")
      val index = bv indexOfSlice CRLFBytes

      if (index >= 0) {
        val (head, tailPre) = bv splitAt index
        val tail = tailPre drop 2       // remove the line feed characters
        val tailM = Some(tail) filter { !_.isEmpty }
        Pull.output1(Either.right(Out(head, tailM))) >> Pull.done
      }
      else if (bv.nonEmpty && bv.get(bv.length - 1) == '\r') {
        // The CRLF may have split across chunks.
        val (head, cr) = bv.splitAt(bv.length - 1)
        Pull.output1(Either.left(head)) >> h.receive1((next, h) => handle(cr ++ next, h))
      }
      else {
        Pull.output1(Either.left(bv)) >> receiveLine[F](None)(h)
      }
    }

    leading.map(handle(_, h)).getOrElse(h.receive1(handle))
  }

  def receiveCollapsedLine[F[_]](leading: Option[ByteVector])(h: Handle[F, ByteVector]): Pull[F, Out[ByteVector], Unit] = {
    receiveLine(leading)(h)
      .close
      .through(pipe.fold(Out(ByteVector.empty)){
      case (acc, Left(partial)) => acc.copy(acc.a ++ partial)
      case (acc, Right(Out(term, tail))) => Out(acc.a ++ term, tail)
    }).output
  }


  def takeUpTo[F[_]](n: Long, headerLimit: Long)(h: Handle[F, ByteVector]): Pull[F, ByteVector, Unit] =
    for {
      (bv, h) <- if (n >= 0) h.await1 else Pull.fail(MalformedMessageBodyFailure(s"Part header was longer than ${headerLimit}-byte limit"))
      out <- Pull.output1(bv) >> takeUpTo(n - bv.size, headerLimit)(h)
    } yield out


//  def header[F[_]](leading: Option[ByteVector], expected: ByteVector)(h: Handle[F, Out[ByteVector]]): Pull[F, Out[Headers], Unit] = {
//    def go(leading: Option[ByteVector])(h: Handle[F, Out[ByteVector]]): Pull[F, Either[Header,Option[ByteVector]], Unit] = {
//      logger.trace(s"Header go: ${leading.map(_.decodeUtf8)} ")
//      h.await1.flatMap{
//        case (Out(bv, tail), h) => {
//          if (bv == expected) {
//            Pull.output1(tail)
//          } else {
//            {
//              for {
//                line <- bv.decodeAscii.right.toOption
//                idx <- Some(line indexOf ':')
//                if idx >= 0
//                if idx < line.length - 1
//              } yield Some(Header(line.substring(0, idx), line.substring(idx + 1).trim))
//            }.map{ header =>
//              Pull.output1(Left(header)) >> go(tail)(h)
//            }.getOrElse(
//              Pull.output1(Right(tail))
//            )
//          }
//        }
//      }
//    }
//    go(leading)(h).map(x => {logger.trace("Got: "+x.toString); x}).close.through(pipe.fold(Out(List.empty[Header])) {
//      case (acc, Left(header)) => acc.copy(a = header :: acc.a)
//      case (acc, Right(tail)) => acc.copy(tail = tail)
//    }).map { case Out(hs, tail) => Out(Headers(hs.reverse), tail) }.output
//  }



}
