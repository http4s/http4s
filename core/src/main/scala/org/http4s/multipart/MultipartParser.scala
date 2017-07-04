package org.http4s
package multipart

import scodec.bits.ByteVector
import fs2._
import cats.implicits._
import fs2.interop.cats._
import fs2.util.syntax._
import fs2.Chunk

import scala.annotation.tailrec

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {


  private[this] val logger = org.log4s.getLogger

//  private val CRLF = "\r\n"
//  private val DASHDASH = "--"
//  private val startLine: Boundary => String = boundary => s"$DASHDASH${boundary.value}"
//  private val endLine: Boundary => String = boundary => s"${startLine(boundary)}$DASHDASH"
//  private val expected: Boundary => String = boundary => s"$CRLF${startLine(boundary)}"

  private val CRLFBytes = ByteVector('\r','\n')
  private val DashDashBytes = ByteVector('-', '-')
  private val boundaryBytes : Boundary => ByteVector = boundary => ByteVector(boundary.value.getBytes)
  private val startLineBytes : Boundary => ByteVector = boundaryBytes andThen (DashDashBytes ++ _)
  private val endLineBytes: Boundary => ByteVector = startLineBytes andThen (_ ++ DashDashBytes)
  private val expectedBytes: Boundary => ByteVector = startLineBytes andThen (CRLFBytes ++ _)

  final case class Out[+A](a: A, tail: Option[ByteVector] = None)

  def parse(boundary: Boundary, headerLimit: Long = 40 * 1024): Pipe[Task, Byte, Either[Headers, Byte]] = s => {
    val bufferedMultipartT = s.runLog.map(vec => ByteVector(vec))
    val parts = bufferedMultipartT.flatMap(parseToParts(_)(boundary))
    val listT = parts.map(splitParts(_)(boundary)(List.empty[Either[Headers, ByteVector]]))

    Stream.eval(listT)
      .flatMap(Stream.emits)
      .through(transformBV)
  }


  /**
    * parseToParts - Removes Prelude and Trailer
    *
    * splitParts - Splits Into Parts
    *   splitPart -
    * @return
    */


  def transformBV: Pipe[Task, Either[Headers, ByteVector], Either[Headers, Byte]] = s => {
    s.flatMap{
      case Left(headers) =>
        Stream.emit(Either.left(headers))
      case Right(bv) => Stream.emits(bv.toSeq).map(Either.right(_))
    }
  }

  @tailrec
  def parseToParts(byteVector: ByteVector)(boundary: Boundary): Task[ByteVector] = {
    val startLine = startLineBytes(boundary)
    val startIndex = byteVector.indexOfSlice(startLine)
    val endLine = endLineBytes(boundary)
    val endIndex = byteVector.indexOfSlice(endLine)

    if (startIndex >= 0 && endIndex >= 0) {
      val parts = byteVector.slice(startIndex + startLine.length + CRLFBytes.length, endIndex - CRLFBytes.length)
      if (parts.containsSlice(startLine)) {
        val droppedInitialPrelude = byteVector.drop(startIndex + startLine.length + CRLFBytes.length)
        parseToParts(droppedInitialPrelude)(boundary)
      } else {

        Task.now(parts)
      }
    } else {
      Task.fail(MalformedMessageBodyFailure("Expected a multipart start or end line"))
    }
  }


  def splitPart(byteVector: ByteVector)(boundary: Boundary): Option[(ByteVector, ByteVector)] = {
    val expected = expectedBytes(boundary)
    val index = byteVector.indexOfSlice(expected)

    if (index >= 0L) {
      val (part, restWithExpected) = byteVector.splitAt(index)
      val rest = restWithExpected.drop(expected.length)
      Option((part, rest))
    } else {
      Option.empty[(ByteVector, ByteVector)]
    }
  }


  @tailrec
  def splitParts(byteVector: ByteVector)
                (boundary: Boundary)
                (acc: List[Either[Headers, ByteVector]]): List[Either[Headers, ByteVector]] = {

    val expected = expectedBytes(boundary)
    val containsExpected = byteVector.containsSlice(expected)

    val partOpt =  if (!containsExpected) {

      Option((byteVector, ByteVector.empty))
    } else {
      splitPart(byteVector)(boundary)
    }

    partOpt match {
      case Some((part, rest)) =>

        val (headers, body) = generatePart(part)

        val newAcc = Either.right(body) :: Either.left(headers) :: acc
//        println(s"splitParts newAcc: ${newAcc}")

        if (rest.isEmpty){
          newAcc.reverse
        } else {
          splitParts(rest.drop(expected.length))(boundary)(newAcc)
        }
      case None => acc.reverse
    }
  }


  def generatePart(byteVector: ByteVector): (Headers, ByteVector) = {
    val doubleCRLF = CRLFBytes ++ CRLFBytes
    val index = byteVector.indexOfSlice(doubleCRLF)
    // Each Part Should Contain this Separation between bodies and headers -- We could handle failure.
    val (headersSplit, bodyWithCRLF) = byteVector.splitAt(index)
    val body = bodyWithCRLF.drop(doubleCRLF.length)

//    println(s"GeneratePart HeadersSplit ${headersSplit.decodeAscii}")
//    println(s"GenerateParts Body ${body.decodeAscii}")

//    val headersBV = headersSplit.dropRight(doubleCRLF.length)
    val headers = generateHeaders(headersSplit ++ CRLFBytes)(Headers.empty)

    (headers, body)
  }

//  @tailrec
  def generateHeaders(byteVector: ByteVector)(acc: Headers) : Headers = {
    val headerO = splitHeader(byteVector)

    headerO match {
      case Some((lineBV, restWithCRLF)) =>
        val headerO = for {
          line <- lineBV.decodeAscii.right.toOption
          idx <- Some(line indexOf ':')
          if idx >= 0
          header = Header(line.substring(0, idx), line.substring(idx + 1).trim)
        } yield header


        val newHeaders = acc ++ headerO

        val rest = restWithCRLF.drop(CRLFBytes.length)

//        println(s"Generate Headers Header0 = $headerO")
//        println(s"Generate Headers rest = ${restWithCRLF.decodeAscii}")

//        newHeaders
        generateHeaders(rest)(newHeaders)
      case None => acc
    }

  }

  def splitHeader(byteVector: ByteVector): Option[(ByteVector, ByteVector)] = {
    val index = byteVector.indexOfSlice(CRLFBytes)

    if (index >= 0L) {
      val (lineWithCRLF, rest) = byteVector.splitAt(index)

//      val line = lineWithCRLF.dropRight(CRLFBytes.length)

//      println(s"Split Header Line: ${lineWithCRLF.decodeAscii}")
//      println(s"Split Header Rest: ${rest.decodeAscii}")
      Option((lineWithCRLF, rest))
    }
    else {
      Option.empty[(ByteVector, ByteVector)]
    }
  }

//  val header : String => Option[Header.Raw] = line => {
//    val idx = line indexOf ':'
//    if (idx >= 0 && idx < line.length - 1) Some(Header(line.substring(0, idx), line.substring(idx + 1).trim))
//    else None
//  }


/*



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

  def header[F[_]](leading: Option[ByteVector], expected: ByteVector)(h: Handle[F, Out[ByteVector]]): Pull[F, Out[Headers], Unit] = {
    def go(leading: Option[ByteVector])(h: Handle[F, Out[ByteVector]]): Pull[F, Either[Header,Option[ByteVector]], Unit] = {
      logger.trace(s"Header go: ${leading.map(_.decodeUtf8)} ")
      h.await1.flatMap{
        case (Out(bv, tail), h) => {
          if (bv == expected) {
            Pull.output1(tail)
          } else {
            {
              for {
                line <- bv.decodeAscii.right.toOption
                idx <- Some(line indexOf ':')
                if idx >= 0
                if idx < line.length - 1
              } yield Some(Header(line.substring(0, idx), line.substring(idx + 1).trim))
            }.map{ header =>
              Pull.output1(Left(header)) >> go(tail)(h)
            }.getOrElse(
              Pull.output1(Right(tail))
            )
          }
        }
      }
    }
    go(leading)(h).map(x => {logger.trace("Got: "+x.toString); x}).close.through(pipe.fold(Out(List.empty[Header])) {
      case (acc, Left(header)) => acc.copy(a = header :: acc.a)
      case (acc, Right(tail)) => acc.copy(tail = tail)
    }).map { case Out(hs, tail) => Out(Headers(hs.reverse), tail) }.output
  }
*/


}
