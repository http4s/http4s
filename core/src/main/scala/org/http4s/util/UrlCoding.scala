/**
 * Taken from https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
 * Copyright (c) 2011 Mojolly Ltd.
 */
package org.http4s.util

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

import scala.collection.immutable.BitSet
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import org.parboiled2.CharPredicate

private[util] trait UrlCodingUtils {

  private val toSkip = BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "!$&'()*+,;=:/?@-._~".toSet).map(_.toInt): _*)
  private val space = ' '.toInt
  private val PctEncoded = """%([0-9a-fA-F][0-9a-fA-F])""".r
  private val LowerPctEncoded = """%([0-9a-f][0-9a-f])""".r
  private val InvalidChars = "[^\\.a-zA-Z0-9!$&'()*+,;=:/?#\\[\\]@-_~]".r

  private val HexUpperCaseChars = (0 until 16) map { i ⇒ Character.toUpperCase(Character.forDigit(i, 16)) }

  private val UTF_8 = "UTF-8"
  private val Utf8 = Charset.forName(UTF_8)

  def isUrlEncoded(string: String) = {
    PctEncoded.findFirstIn(string).isDefined
  }

  def containsInvalidUriChars(string: String) = {
    InvalidChars.findFirstIn(string).isDefined
  }

  def needsUrlEncoding(string: String) = {
    !isUrlEncoded(string) && containsInvalidUriChars(string)
  }

  def ensureUrlEncoding(string: String) = if (needsUrlEncoding(string)) urlEncode(string) else string

  def ensureUppercasedEncodings(string: String) = {
    LowerPctEncoded.replaceAllIn(string, (_: Match) match {
      case Regex.Groups(v) => "%" + v.toUpperCase(Locale.ENGLISH)
    })
  }

  /**
    * Percent-encodes a string.  Depending on the parameters, this method is
    * appropriate for URI or URL form encoding.  Any resulting percent-encodings
    * are normalized to uppercase.
    * 
    * Characeters in `toSkip` are rendered as is.  If a skipped '%' is followed
    * by exactly two hex digits, they are uppercased for normalization purposes.
    * 
    * Space is rendered as `" "` if skipped.  If space is not skipped, it is
    * encoded as `"+"` when `spaceIsPlus` is true, or percent encoded otherwise.
    * 
    * All characters not in toSkip are rendered in a percent encoding, according
    * to `charset`.
    */
  def urlEncode(toEncode: String, charset: Charset = Utf8, spaceIsPlus: Boolean = false, toSkip: BitSet = toSkip) = {
    val in = charset.encode(toEncode)
    val out = CharBuffer.allocate((in.remaining() * 3).ceil.toInt)
    while (in.hasRemaining) {
      val b = in.get() & 0xFF
      if (toSkip.contains(b)) {
        out.put(b.toInt.toChar)
        if (b == '%' && in.hasRemaining) {
          in.mark()
          val c0 = in.get().toChar
          if (CharPredicate.HexDigit(c0) && in.hasRemaining) {
            val c1 = in.get().toChar
            if (CharPredicate.HexDigit(c1)) {
              out.put(c0.toUpper)
              out.put(c1.toUpper)
            } else {
              in.reset()
            }
          } else {
            in.reset()
          }
        }
      } else if (b == space && spaceIsPlus) {
        out.put('+')
      } else {
        out.put('%')
        out.put(HexUpperCaseChars((b >> 4) & 0xF))
        out.put(HexUpperCaseChars(b & 0xF))
      }
    }
    out.flip()
    out.toString
  }

  def urlDecode(toDecode: String, charset: Charset = Utf8, plusIsSpace: Boolean = false, toSkip: BitSet = BitSet.empty) = {
    val in = CharBuffer.wrap(toDecode)
    // reserve enough space for 3-byte UTF-8 characters.  4-byte characters are represented
    // as surrogate pairs of characters, and will get a luxurious 6 bytes of space.
    val out = ByteBuffer.allocate(in.remaining() * 3)
    while (in.hasRemaining) {
      val mark = in.position()
      val c = in.get()
      if (c == '%') {
        if (in.remaining() >= 2) {
          val xc = in.get()
          val yc = in.get()
          val x = Character.digit(xc, 0x10)
          val y = Character.digit(yc, 0x10)
          if (x != -1 && y != -1) {
            val oo = (x << 4) + y
            if (!toSkip.contains(oo)) {
              out.put(oo.toByte)
            } else {
              out.put('%'.toByte)
              out.put(xc.toByte)
              out.put(yc.toByte)
            }
          } else {
            out.put('%'.toByte)
            in.position(mark+1)
          }
        } else {
          while(in.hasRemaining) in.get() // just burn the rest of the bytes
        }
      } else if (c == '+' && plusIsSpace) {
        out.put(' '.toByte)
      } else {
        // normally `out.put(c.toByte)` would be enough since the url is %-encoded,
        // however there are cases where a string can be partially decoded
        // so we have to make sure the non us-ascii chars get preserved properly.
        if (this.toSkip.contains(c))
          out.put(c.toByte)
        else {
          out.put(charset.encode(String.valueOf(c)))
        }
      }
    }
    out.flip()
    charset.decode(out).toString
  }

}

object UrlCodingUtils extends UrlCodingUtils
