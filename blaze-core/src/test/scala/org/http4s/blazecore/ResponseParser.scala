package org.http4s
package blazecore

import cats.implicits._
import fs2._
import fs2.interop.scodec.ByteVectorChunk
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.http.http_parser.Http1ClientParser
import org.http4s.util.chunk.ByteChunkMonoid
import scala.collection.mutable.ListBuffer
import scodec.bits.ByteVector

class ResponseParser extends Http1ClientParser {

  val headers = new ListBuffer[(String, String)]

  var code: Int = -1
  var reason = ""
  var scheme = ""
  var majorversion = -1
  var minorversion = -1

  /** Will not mutate the ByteBuffers in the Seq */
  def parseResponse(buffs: Seq[ByteBuffer]): (Status, Set[Header], String) = {
    val b =
      ByteBuffer.wrap(buffs.map(b => ByteVectorChunk(ByteVector.view(b)).toArray).toArray.flatten)
    parseResponseBuffer(b)
  }

  /* Will mutate the ByteBuffer */
  def parseResponseBuffer(buffer: ByteBuffer): (Status, Set[Header], String) = {
    parseResponseLine(buffer)
    parseHeaders(buffer)

    if (!headersComplete()) sys.error("Headers didn't complete!")

    val body = new ListBuffer[ByteBuffer]
    while (!this.contentComplete() && buffer.hasRemaining) {
      body += parseContent(buffer)
    }

    val bp = {
      val bytes = body.toList.foldMap[Chunk[Byte]](bb => ByteVectorChunk(ByteVector.view(bb)))
      new String(bytes.toBytes.values, StandardCharsets.ISO_8859_1)
    }

    val headers = this.headers.result.map { case (k, v) => Header(k, v): Header }.toSet

    val status = Status.fromIntAndReason(this.code, reason).valueOr(throw _)

    (status, headers, bp)
  }

  override protected def headerComplete(name: String, value: String): Boolean = {
    headers += ((name, value))
    false
  }

  override protected def submitResponseLine(
      code: Int,
      reason: String,
      scheme: String,
      majorversion: Int,
      minorversion: Int): Unit = {
    this.code = code
    this.reason = reason
    this.majorversion = majorversion
    this.minorversion = minorversion
  }
}

object ResponseParser {
  def apply(buff: Seq[ByteBuffer]): (Status, Set[Header], String) =
    new ResponseParser().parseResponse(buff)
  def apply(buff: ByteBuffer): (Status, Set[Header], String) = parseBuffer(buff)

  def parseBuffer(buff: ByteBuffer): (Status, Set[Header], String) =
    new ResponseParser().parseResponseBuffer(buff)
}
