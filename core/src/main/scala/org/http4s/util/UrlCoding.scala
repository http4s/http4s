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

private[http4s] object UrlCodingUtils {

  val Unreserved = 
    CharPredicate.AlphaNum ++ "-_.~"

  private val toSkip =
    Unreserved ++ "!$&'()*+,;=:/?@"

  // scalastyle:off magic.number
  private val HexUpperCaseChars = (0 until 16) map { i => Character.toUpperCase(Character.forDigit(i, 16)) }
  // scalastyle:on magic.number

  /**
    * Percent-encodes a string.  Depending on the parameters, this method is
    * appropriate for URI or URL form encoding.  Any resulting percent-encodings
    * are normalized to uppercase.
    * 
    * @param toEncode the string to encode
    * @param charset the charset to use for characters that are percent encoded
    * @param spaceIsPlus if space is not skipped, determines whether it will be
    * rendreed as a `"+"` or a percent-encoding according to `charset`.
    * @param toSkip a predicate of characters exempt from encoding.  In typical
    * use, this is composed of all Unreserved URI characters and sometimes a
    * subset of Reserved URI characters.
    */
  def urlEncode(toEncode: String, charset: Charset = UTF_8, spaceIsPlus: Boolean = false, toSkip: Char => Boolean = toSkip): String = {
    val in = charset.encode(toEncode)
    val out = CharBuffer.allocate((in.remaining() * 3).toInt)
    while (in.hasRemaining) {
      val c = in.get().toChar
      if (toSkip(c)) {
        out.put(c)
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

  /**
    * Percent-decodes a string.
    * 
    * @param toDecode the string to decode
    * @param charset the charset of percent-encoded characters
    * @param plusIsSpace true if `'+'` is to be interpreted as a `' '`
    * @param toSkip a predicate of characters whose percent-encoded form
    * is left percent-encoded.  Almost certainly should be left empty.
    */
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
          // This is an invalid encoding. Fail gracefully by treating the '%' as
          // a literal.
          out.put(c.toByte)
          while(in.hasRemaining) out.put(in.get().toByte)
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

