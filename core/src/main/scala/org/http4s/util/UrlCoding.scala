/**
 * Taken from https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
 * Copyright (c) 2011 Mojolly Ltd.
 */
package org.http4s.util

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import org.parboiled2.CharPredicate

private[http4s] object UrlCodingUtils {

  val Unreserved = 
    CharPredicate.AlphaNum ++ "-_.~"

  private val toSkip =
    Unreserved ++ "!$&'()*+,;=:/?@"

  // scalastyle:off magic.number
  private val HexUpperCaseChars = (0 until 16) map { i ⇒ Character.toUpperCase(Character.forDigit(i, 16)) }
  // scalastyle:on magic.number

  def urlEncode(toEncode: String, charset: Charset = UTF_8, spaceIsPlus: Boolean = false, toSkip: Char => Boolean = toSkip): String = {
    val in = charset.encode(toEncode)
    val out = CharBuffer.allocate((in.remaining() * 3).ceil.toInt)
    while (in.hasRemaining) {
      val c = in.get().toChar
      if (toSkip(c)) {
        out.put(c)
        if (c == '%' && in.hasRemaining) {
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
      } else if (c == ' ' && spaceIsPlus) {
        out.put('+')
      } else {
        out.put('%')
        out.put(HexUpperCaseChars((c >> 4) & 0xF))
        out.put(HexUpperCaseChars(c & 0xF))
      }
    }
    out.flip()
    out.toString
  }

  private val SkipEncodeInPath =
    Unreserved ++ ":@!$&'()*+,;="

  def pathEncode(s: String, charset: Charset = UTF_8): String =
    UrlCodingUtils.urlEncode(s, charset, false, SkipEncodeInPath)

  def urlDecode(toDecode: String, charset: Charset = UTF_8, plusIsSpace: Boolean = false, toSkip: Char => Boolean = Function.const(false)): String = {
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
          // scalastyle:off magic.number
          val x = Character.digit(xc, 0x10)
          val y = Character.digit(yc, 0x10)
          // scalastyle:on magic.number
          if (x != -1 && y != -1) {
            val oo = (x << 4) + y
            if (!toSkip(oo.toChar)) {
              out.put(oo.toByte)
            } else {
              out.put('%'.toByte)
              out.put(xc.toByte)
              out.put(yc.toByte)
            }
          } else {
            out.put('%'.toByte)
            in.position(mark + 1)
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
        if (this.toSkip(c)) {
          out.put(c.toByte)
        }
        else {
          out.put(charset.encode(String.valueOf(c)))
        }
      }
    }
    out.flip()
    charset.decode(out).toString
  }
}

