/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package parser

import java.io.UnsupportedEncodingException
import java.nio.CharBuffer
import scala.collection.immutable.BitSet
import scala.collection.mutable.Builder
import scala.io.Codec

/** Split an encoded query string into unencoded key value pairs
  * It always assumes any input is a  valid query, including "".
  * If "" should be interpreted as no query that __MUST__ be
  * checked beforehand.
  */
private[http4s] class QueryParser(
    codec: Codec,
    colonSeparators: Boolean,
    qChars: BitSet = QueryParser.ExtendedQChars,
) {
  import QueryParser._

  /** Decodes the input into key value pairs.
    * `flush` signals that this is the last input
    */
  def decode(input: CharBuffer, flush: Boolean): ParseResult[Query] =
    decodeVector(input, flush).map(Query.fromVector)

  /** Decodes the input into key value pairs.
    * `flush` signals that this is the last input
    */
  def decodeVector(input: CharBuffer, flush: Boolean): ParseResult[Vector[Query.KeyValue]] = {
    val acc: Builder[Query.KeyValue, Vector[Query.KeyValue]] = Vector.newBuilder
    decodeBuffer(input, (k, v) => acc += ((k, v)), flush) match {
      case Some(e) => ParseResult.fail("Decoding of url encoded data failed.", e)
      case None => ParseResult.success(acc.result())
    }
  }

  // Some[String] represents an error message, None = success
  private def decodeBuffer(
      input: CharBuffer,
      acc: (String, Option[String]) => Builder[Query.KeyValue, Vector[Query.KeyValue]],
      flush: Boolean,
  ): Option[String] = {
    val valAcc = new StringBuilder(InitialBufferCapactiy)

    var error: String = null
    var key: String = null
    var state: State = KEY

    def appendValue(): Unit = {
      if (state == KEY) {
        val s = valAcc.result()
        val k = decodeParam(s)
        valAcc.clear()
        acc(k, None)
      } else {
        val k = decodeParam(key)
        key = null
        val s = valAcc.result()
        valAcc.clear()
        val v = Some(decodeParam(s))
        acc(k, v)
      }
      ()
    }

    def endPair(): Unit = {
      if (!flush) {
        input.mark()
        ()
      }
      appendValue()
      state = KEY
    }

    if (!flush) {
      input.mark()
      ()
    }

    // begin iterating through the chars
    while (error == null && input.hasRemaining) {
      val c = input.get()
      c match {
        case '&' => endPair()

        case ';' if colonSeparators => endPair()

        case '=' =>
          if (state == VALUE) valAcc.append('=')
          else {
            state = VALUE
            key = valAcc.result()
            valAcc.clear()
          }

        case c if qChars.contains(c.toInt) => valAcc.append(c)

        case c => error = s"Invalid char while splitting key/value pairs: '$c'"
      }
    }
    if (error != null) Some(error)
    else {
      if (flush) appendValue()
      else input.reset() // rewind to the last mark position
      None
    }
  }

  private def decodeParam(str: String): String =
    try Uri.decode(str, codec.charSet, plusIsSpace = true)
    catch {
      case _: IllegalArgumentException => ""
      case _: UnsupportedEncodingException => ""
    }
}

private[http4s] object QueryParser {
  private val InitialBufferCapactiy = 32

  def parseQueryString(queryString: String, codec: Codec = Codec.UTF8): ParseResult[Query] =
    if (queryString.isEmpty) Right(Query.empty)
    else new QueryParser(codec, true).decode(CharBuffer.wrap(queryString), true)

  def parseQueryStringVector(
      queryString: String,
      codec: Codec = Codec.UTF8,
  ): ParseResult[Vector[Query.KeyValue]] =
    if (queryString.isEmpty) Right(Vector.empty)
    else new QueryParser(codec, true).decodeVector(CharBuffer.wrap(queryString), true)

  private sealed trait State
  private case object KEY extends State
  private case object VALUE extends State

  /** Defines the characters that are allowed unquoted within a query string as
    * defined in RFC 3986
    */
  val QChars: BitSet = BitSet((Pchar ++ "/?".toSet - '&' - '=').map(_.toInt).toSeq: _*)

  /** PHP also includes square brackets ([ and ]) with query strings. This goes
    * against the spec but due to PHP's widespread adoption it is necessary to
    * support this extension.
    */
  val ExtendedQChars: BitSet = QChars ++ ("[]".map(_.toInt).toSet)
  private def Pchar = Unreserved ++ SubDelims ++ ":@%".toSet
  private def Unreserved = "-._~".toSet ++ AlphaNum
  private def SubDelims = "!$&'()*+,;=".toSet
  private def AlphaNum = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
}
