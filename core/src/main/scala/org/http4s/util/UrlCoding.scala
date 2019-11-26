/**
  * Taken from https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
  * Copyright (c) 2011 Mojolly Ltd.
  */
package org.http4s.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8

private[http4s] object UrlCodingUtils {

  @deprecated("Moved to org.http4s.Uri.Unreserved", "0.20.13")
  val Unreserved =
    org.http4s.Uri.Unreserved

  val GenDelims =
    CharPredicate.from(":/?#[]@".toSet)

  val SubDelims =
    CharPredicate.from("!$&'()*+,;=".toSet)

  private val toSkip =
    org.http4s.Uri.Unreserved ++ "!$&'()*+,;=:/?@"

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
  @deprecated("Moved to org.http4s.Uri.encode", "0.20.13")
  def urlEncode(
      toEncode: String,
      charset: Charset = UTF_8,
      spaceIsPlus: Boolean = false,
      toSkip: Char => Boolean = toSkip): String =
    org.http4s.Uri.encode(toEncode, charset, spaceIsPlus, toSkip)

  @deprecated("Moved to org.http4s.Uri.pathEncode", "0.20.13")
  def pathEncode(s: String, charset: Charset = UTF_8): String =
    org.http4s.Uri.pathEncode(s, charset)

  /**
    * Percent-decodes a string.
    *
    * @param toDecode the string to decode
    * @param charset the charset of percent-encoded characters
    * @param plusIsSpace true if `'+'` is to be interpreted as a `' '`
    * @param toSkip a predicate of characters whose percent-encoded form
    * is left percent-encoded.  Almost certainly should be left empty.
    */
  @deprecated("Moved to org.http4s.Uri.decode", "0.20.13")
  def urlDecode(
      toDecode: String,
      charset: Charset = UTF_8,
      plusIsSpace: Boolean = false,
      toSkip: Char => Boolean = Function.const(false)): String =
    org.http4s.Uri.decode(toDecode, charset, plusIsSpace, toSkip)
}
