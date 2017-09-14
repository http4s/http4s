package org.http4s
package multipart

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import scala.annotation.tailrec
import scodec.bits.ByteVector

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {

  private[this] val logger = org.log4s.getLogger

  private val CRLFBytes = ByteVector('\r', '\n')
  private val DashDashBytes = ByteVector('-', '-')
  private val boundaryBytes: Boundary => ByteVector = boundary =>
    ByteVector(boundary.value.getBytes)
  private val startLineBytes: Boundary => ByteVector = boundaryBytes.andThen(DashDashBytes ++ _)
  private val endLineBytes: Boundary => ByteVector = startLineBytes.andThen(_ ++ DashDashBytes)
  private val expectedBytes: Boundary => ByteVector = startLineBytes.andThen(CRLFBytes ++ _)

  final case class Out[+A](a: A, tail: Option[ByteVector] = None)

  def parse[F[_]: Sync](
      boundary: Boundary,
      headerLimit: Long = 40 * 1024): Pipe[F, Byte, Either[Headers, ByteVector]] = s => {
    val bufferedMultipartT = s.runLog.map(vec => ByteVector(vec))
    val parts = bufferedMultipartT.flatMap(parseToParts(_)(boundary))
    val listT = parts.map(splitParts(_)(boundary)(List.empty[Either[Headers, ByteVector]]))

    Stream
      .eval(listT)
      .flatMap(list => Stream.emits(list))
  }

  /**
    * parseToParts - Removes Prelude and Trailer
    *
    * splitParts - Splits Into Parts
    * splitPart - Takes a Single Part of the Front
    * generatePart - Generates a tuple of Headers and a ByteVector of the Body, effectively a Part
    *
    * generateHeaders - Generate Headers from ByteVector
    * splitHeader - Splits a Header into the Name and Value
    */
  def parseToParts[F[_]](byteVector: ByteVector)(boundary: Boundary)(
      implicit F: Sync[F]): F[ByteVector] = {
    val startLine = startLineBytes(boundary)
    val startIndex = byteVector.indexOfSlice(startLine)
    val endLine = endLineBytes(boundary)
    val endIndex = byteVector.indexOfSlice(endLine)

    if (startIndex >= 0 && endIndex >= 0) {
      val parts = byteVector.slice(
        startIndex + startLine.length + CRLFBytes.length,
        endIndex - CRLFBytes.length)
      F.delay(parts)
    } else {
      F.raiseError(MalformedMessageBodyFailure("Expected a multipart start or end line"))
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
      Option((byteVector, ByteVector.empty))
    }
  }
  @tailrec
  def splitParts(byteVector: ByteVector)(boundary: Boundary)(
      acc: List[Either[Headers, ByteVector]]): List[Either[Headers, ByteVector]] = {

    val expected = expectedBytes(boundary)
    val containsExpected = byteVector.containsSlice(expected)

    val partOpt = if (!containsExpected) {

      Option((byteVector, ByteVector.empty))
    } else {
      splitPart(byteVector)(boundary)
    }

    partOpt match {
      case Some((part, rest)) =>
        logger.trace(s"splitParts part: ${part.decodeUtf8.toOption}")

        val (headers, body) = generatePart(part)

        val newAcc = Either.right(body) :: Either.left(headers) :: acc
        logger.trace(s"splitParts newAcc: $newAcc")

        if (rest.isEmpty) {
          newAcc.reverse
        } else {
          splitParts(rest)(boundary)(newAcc)
        }
      case None => acc.reverse
    }
  }

  def generatePart(byteVector: ByteVector): (Headers, ByteVector) = {
    val doubleCRLF = CRLFBytes ++ CRLFBytes
    val index = byteVector.indexOfSlice(doubleCRLF)
    // Each Part Should Contain this Separation between bodies and headers -- We could handle failure.
    val (headersSplit, bodyWithCRLFs) = byteVector.splitAt(index)
    val body = bodyWithCRLFs.drop(doubleCRLF.length)

    logger.trace(s"GeneratePart HeadersSplit ${headersSplit.decodeAscii}")
    logger.trace(s"GenerateParts Body ${body.decodeAscii}")

    val headers = generateHeaders(headersSplit ++ CRLFBytes)(Headers.empty)

    (headers, body)
  }

  @tailrec
  def generateHeaders(byteVector: ByteVector)(acc: Headers): Headers = {
    val headerO = splitHeader(byteVector)

    headerO match {
      case Some((lineBV, rest)) =>
        val headerO = for {
          line <- lineBV.decodeAscii.right.toOption
          idx <- Some(line.indexOf(':'))
          if idx >= 0
          header = Header(line.substring(0, idx), line.substring(idx + 1).trim)
        } yield header

        val newHeaders = acc ++ headerO

        logger.trace(s"Generate Headers Header0 = $headerO")
        generateHeaders(rest)(newHeaders)
      case None => acc
    }

  }

  def splitHeader(byteVector: ByteVector): Option[(ByteVector, ByteVector)] = {
    val index = byteVector.indexOfSlice(CRLFBytes)

    if (index >= 0L) {
      val (line, rest) = byteVector.splitAt(index)

      logger.trace(s"Split Header Line: ${line.decodeAscii}")
      logger.trace(s"Split Header Rest: ${rest.decodeAscii}")
      Option((line, rest.drop(CRLFBytes.length)))
    } else {
      Option.empty[(ByteVector, ByteVector)]
    }
  }
}
