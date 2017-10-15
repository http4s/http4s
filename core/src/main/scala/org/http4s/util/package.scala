package org.http4s

import fs2._
import fs2.interop.scodec.ByteVectorChunk
import java.net.{Inet6Address, InetAddress}
import java.nio.{ByteBuffer, CharBuffer}
import java.util.Arrays
import scala.concurrent.ExecutionContextExecutor
import scala.util.control.NonFatal
import scodec.bits.ByteVector

package object util {
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.nioCharset.newDecoder
    val maxCharsPerByte = math.ceil(decoder.maxCharsPerByte().toDouble).toInt
    val avgBytesPerChar = math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toLong
    val charBufferSize = 128L

    _.repeatPull[String] {
      _.unconsN(charBufferSize * avgBytesPerChar, allowFewer = true).flatMap {
        case None =>
          val charBuffer = CharBuffer.allocate(1)
          decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
          decoder.flush(charBuffer)
          val outputString = charBuffer.flip().toString
          if (outputString.isEmpty) Pull.done.as(None)
          else Pull.output1(outputString).as(None)
        case Some((segment, stream)) =>
          val byteVector = ByteVector(segment.toVector)
          val byteBuffer = byteVector.toByteBuffer
          val charBuffer = CharBuffer.allocate(byteVector.size.toInt * maxCharsPerByte)
          decoder.decode(byteBuffer, charBuffer, false)
          val nextByteVector = ByteVector.view(byteBuffer.slice)
          val nextStream = stream.consChunk(ByteVectorChunk(nextByteVector))
          Pull.output1(charBuffer.flip().toString).as(Some(nextStream))
      }
    }
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(
      s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  private[http4s] def tryCatchNonFatal[A](f: => A): Either[Throwable, A] =
    try Right(f)
    catch { case NonFatal(t) => Left(t) }

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax

  @deprecated(
    "Moved to org.http4s.execution.trampoline, is now merely a ExecutionContextExecutor.",
    "0.18.0-M2")
  val TrampolineExecutionContext: ExecutionContextExecutor = execution.trampoline

  /* This is nearly identical to the hashCode of java.lang.String, but converting
   * to lower case on the fly to avoid copying `value`'s character storage.
   */
  def hashLower(s: String): Int = {
    var h = 0
    var i = 0
    val len = s.length
    while (i < len) {
      // Strings are equal igoring case if either their uppercase or lowercase
      // forms are equal. Equality of one does not imply the other, so we need
      // to go in both directions. A character is not guaranteed to make this
      // round trip, but it doesn't matter as long as all equal characters
      // hash the same.
      h = h * 31 + Character.toLowerCase(Character.toUpperCase(s.charAt(i)))
      i += 1
    }
    h
  }

  /** A port of Guava's */
  // https://github.com/google/guava/blob/6ded67ff124f1be4f318dbbeae136d0c995faf37/guava/src/com/google/common/net/InetAddresses.java#L341
  def toAddrString(ip: InetAddress): String = {
    def fromBytes(b1: Byte, b2: Byte, b3: Byte, b4: Byte) =
      b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF)

    def compressLongestRunOfZeroes(hextets: Array[Int]) = {
      var bestRunStart = -1
      var bestRunLength = -1
      var runStart = -1
      for (i <- 0 to hextets.length) {
        if (i < hextets.length && hextets(i) == 0) {
          if (runStart < 0) {
            runStart = i
          }
        } else if (runStart >= 0) {
          val runLength = i - runStart
          if (runLength > bestRunLength) {
            bestRunStart = runStart
            bestRunLength = runLength
          }
          runStart = -1
        }
      }
      if (bestRunLength >= 2)
        Arrays.fill(hextets, bestRunStart, bestRunStart + bestRunLength, -1)
    }

    def hextetsToIPv6String(hextets: Array[Int]) = {
      val buf = new StringBuilder(39)
      var lastWasNumber = false
      for (i <- 0 until hextets.length) {
        val thisIsNumber = hextets(i) >= 0
        if (thisIsNumber) {
          if (lastWasNumber) {
            buf.append(':')
          }
          buf.append(Integer.toHexString(hextets(i)))
        } else {
          if (i == 0 || lastWasNumber) {
            buf.append("::")
          }
        }
        lastWasNumber = thisIsNumber
      }
      buf.toString
    }

    ip match {
      case ipv6: Inet6Address =>
        val bytes = ipv6.getAddress
        val hextets = new Array[Int](8)
        for (i <- 0 until hextets.length)
          hextets(i) = fromBytes(0: Byte, 0: Byte, bytes(2 * i), bytes(2 * i + 1))
        compressLongestRunOfZeroes(hextets)
        hextetsToIPv6String(hextets)
      case _ =>
        ip.getHostAddress
    }
  }
}
