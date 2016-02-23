package org.http4s.parser

import java.net.URLDecoder
import java.nio.CharBuffer
import org.http4s._

import scala.annotation.switch
import scala.collection.immutable.BitSet
import scala.io.Codec

import QueryParser._

import scalaz.{-\/, \/-, \/}

/** Split an encoded query string into unencoded key value pairs
  * It always assumes any input is a  valid query, including "".
  * If "" should be interpreted as no query that __MUST__ be
  * checked beforehand.
  */
private[http4s] class QueryParser(codec: Codec, colonSeparators: Boolean, qChars: BitSet = QueryParser.ExtendedQChars) {

  /** Decodes the input into key value pairs.
    * `flush` signals that this is the last input */
  def decode(input: CharBuffer, flush: Boolean): ParseResult[FormQuery] = {
    val acc = FormQuery.newBuilder
    decodeBuffer(input, (k,v) => acc += ((k,v)), flush) match {
      case Some(e) => ParseResult.fail("Decoding of url encoded data failed.", e)
      case None    => ParseResult.success(acc.result)
    }
  }

  // Some[String] represents an error message, None = success
  def decodeBuffer(input: CharBuffer, acc: (String, Option[String]) => Unit, flush: Boolean): Option[String] = {
    val valAcc = new StringBuilder(32)

    var error: String = null
    var key: String = null
    var state: State = KEY

    def appendValue(): Unit = {
      if (state == KEY) {
        val s = valAcc.result()
        val k = decodeParam(s)
        valAcc.clear()
        acc(k, None)
      }
      else {
        val k = decodeParam(key)
        key = null
        val s = valAcc.result()
        valAcc.clear()
        val v = Some(decodeParam(s))
        acc(k, v)
      }
    }

    def endPair(): Unit = {
      if (!flush) input.mark()
      appendValue()
      state = KEY
    }

    if (!flush) input.mark()

    // begin iterating through the chars
    while(error == null && input.hasRemaining) {
      val c = input.get()
      (c: @switch) match {
        case '&' => endPair()

        case ';' if colonSeparators => endPair()

        case '=' =>
          if (state == VALUE) valAcc.append('=')
          else {
            state = VALUE
            key = valAcc.result()
            valAcc.clear()
          }

        case c if (qChars.contains(c)) => valAcc.append(c)

        case c => error = s"Invalid char while splitting key/value pairs: '$c'"
      }
    }
    if (error != null) Some(error)
    else {
      if (flush) appendValue()
      else input.reset()    // rewind to the last mark position
      None
    }
  }

  private def decodeParam(str: String): String =
    URLDecoder.decode(str, codec.charSet.name())
}

private[http4s] object QueryParser {
  def parseQueryString(queryString: String, codec: Codec = Codec.UTF8): ParseResult[Query] = {
    if (queryString.isEmpty) \/-(Query.none)
    else new QueryParser(codec, true).decode(CharBuffer.wrap(queryString), true)
  }

  private sealed trait State
  private case object KEY extends State
  private case object VALUE extends State

  /** Defines the characters that are allowed unquoted within a query string as
    * defined in RFC 3986*/
  val QChars = BitSet((Pchar ++ "/?".toSet - '&' - '=').map(_.toInt).toSeq:_*)
  /** PHP also includes square brackets ([ and ]) with query strings. This goes
    * against the spec but due to PHP's widespread adoption it is necessary to
    * support this extension. */
  val ExtendedQChars = QChars ++ ("[]".map(_.toInt).toSet)
  private def Pchar = Unreserved ++ SubDelims ++ ":@%".toSet
  private def Unreserved =  "-._~".toSet ++ AlphaNum
  private def SubDelims  = "!$&'()*+,;=".toSet
  private def AlphaNum   = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
}
