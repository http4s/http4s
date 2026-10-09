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

package org.http4s.websocket

import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.hashing.MurmurHash3

abstract class WebSocketFrame {
  def opcode: Int
  def data: ByteVector
  def last: Boolean

  final def length: Int = data.length.toInt

  override def equals(obj: Any): Boolean =
    obj match {
      case wf: WebSocketFrame =>
        this.opcode == wf.opcode &&
        this.last == wf.last &&
        this.data == wf.data
      case _ => false
    }

  override def hashCode: Int = {
    var hash = WebSocketFrame.hashSeed
    hash = MurmurHash3.mix(hash, opcode.##)
    hash = MurmurHash3.mix(hash, data.##)
    hash = MurmurHash3.mixLast(hash, last.##)
    hash
  }
}

object WebSocketFrame {
  private val hashSeed = MurmurHash3.stringHash("WebSocketFrame")

  sealed abstract class ControlFrame extends WebSocketFrame {
    final def last: Boolean = true
  }

  sealed abstract class Text extends WebSocketFrame {
    def str: String
    def opcode: Int = TEXT

    override def toString: String = s"Text('$str', last: $last)"
  }

  private class BinaryText(val data: ByteVector, val last: Boolean) extends Text {
    lazy val str: String = new String(data.toArray, UTF_8)
  }

  private class StringText(override val str: String, val last: Boolean) extends Text {
    lazy val data: ByteVector = ByteVector.view(str.getBytes(UTF_8))
  }

  object Text {
    def apply(str: String, last: Boolean = true): Text = new StringText(str, last)
    def apply(data: ByteVector, last: Boolean): Text = new BinaryText(data, last)
    def apply(data: ByteVector): Text = new BinaryText(data, last = true)
    def unapply(txt: Text): Option[(String, Boolean)] = Some((txt.str, txt.last))
  }

  final case class Binary(data: ByteVector, last: Boolean = true) extends WebSocketFrame {
    def opcode: Int = BINARY
    override def toString: String = s"Binary(Array(${data.length}), last: $last)"
  }

  final case class Continuation(data: ByteVector, last: Boolean) extends WebSocketFrame {
    def opcode: Int = CONTINUATION
    override def toString: String = s"Continuation(Array(${data.length}), last: $last)"
  }

  final case class Ping(data: ByteVector = ByteVector.empty) extends ControlFrame {
    def opcode: Int = PING
    override def toString: String =
      if (data.length > 0) s"Ping(Array(${data.length}))"
      else s"Ping"
  }

  final case class Pong(data: ByteVector = ByteVector.empty) extends ControlFrame {
    def opcode: Int = PONG
    override def toString: String =
      if (data.length > 0) s"Pong(Array(${data.length}))"
      else s"Pong"
  }

  final case class Close(data: ByteVector = ByteVector.empty) extends ControlFrame {
    def opcode: Int = CLOSE

    def closeCode: Int =
      if (data.length > 0)
        (data(0) << 8 & 0xff00) | (data(1) & 0xff) // 16-bit unsigned
      else 1005 // No code present

    def reason: String =
      if (data.length > 2)
        new String(data.drop(2).toArray, UTF_8)
      else ""

    override def toString: String =
      if (data.length > 0) s"Close(Array(${data.length}))"
      else s"Close"
  }

  /** Encodes the predefined and reserved status codes for a WebSocket Close frame.
    * These codes indicate the reason for closing an established connection.
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc6455#section-7.4.1]]
    * @see [[https://www.iana.org/assignments/websocket#close-code-number]]
    */
  sealed abstract class CloseStatusCode(val code: Int) extends Product with Serializable

  object CloseStatusCode {

    /** Indicates a normal closure, meaning that the purpose for
      * which the connection was established has been fulfilled.
      */
    case object Normal extends CloseStatusCode(1000)

    /** Indicates that an endpoint is "going away", such as a server going down, or a browser
      * having navigated away from a page.
      */
    case object GoingAway extends CloseStatusCode(1001)

    /** Indicates that an endpoint is terminating the connection due to a protocol error.
      */
    case object ProtocolError extends CloseStatusCode(1002)

    /** Indicates that an endpoint is terminating the connection because it has received
      * a type of data it cannot accept.
      */
    case object UnsupportedType extends CloseStatusCode(1003)

    /* Reserved. The specific meaning might be defined in the future. */
    case object Reserved extends CloseStatusCode(1004)

    /** Reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.
      * It is designated for use in applications expecting a status code to indicate that no status
      * code was actually present.
      */
    case object NoCode extends CloseStatusCode(1005)

    /** Reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.
      * It is designated for use in applications expecting a status code to indicate that the connection
      * was closed abnormally
      */
    case object AbnormalClose extends CloseStatusCode(1006)

    /** Indicates that an endpoint is terminating the connection because it has received data
      * within a message that was not consistent with the type of the message.
      */
    case object NoUtf8 extends CloseStatusCode(1007)

    /** Indicates that an endpoint is terminating the connection because it has received a message
      * that violates its policy. This is a generic status code that can be returned when there is no
      * other more suitable status code (e.g. 1003 or 1009), or if there is a need to hide specific
      * details about the policy.
      */
    case object PolicyValidation extends CloseStatusCode(1008)

    /** Indicates that an endpoint is terminating the connection because it has received a message
      * which is too big for it to process.
      */
    case object TooBig extends CloseStatusCode(1009)

    /** Indicates that an endpoint (client) is terminating the connection because it has expected
      * the server to negotiate one or more extensions, but the server didn't return them in the
      * response message of the WebSocket handshake. The list of extensions that are needed SHOULD
      * appear in the `reason` part of the Close frame. Note that this status code is not used by the
      * server, because it can fail the WebSocket handshake instead.
      */
    case object Extension extends CloseStatusCode(1010)

    /** Indicates that a server is terminating the connection because it encountered an unexpected
      * condition that prevented it from fulfilling the request.
      */
    case object UnexpectedCondition extends CloseStatusCode(1011)

    /** Indicates that the service is restarted. A client may reconnect, and if it chooses to do,
      * should reconnect using a randomized delay of 5-30 seconds.
      */
    case object ServiceRestart extends CloseStatusCode(1012)

    /** Indicates that the service is experiencing overload. A client should only connect to a
      * different IP (when there are multiple for the target) or reconnect to the same IP upon user
      * action.
      */
    case object TryAgainLater extends CloseStatusCode(1013)

    /** Indicates that the server was acting as a gateway or proxy and received an invalid
      * response from the upstream server. This is similar to 502 HTTP Status Code.
      */
    case object BadGateway extends CloseStatusCode(1014)

    /** Reserved value and MUST NOT be set as a status code in a Close control frame by an
      * endpoint. It is designated for use in applications expecting a status code to indicate that the
      * connection was closed due to a failure to perform a TLS handshake.
      */
    case object TlsError extends CloseStatusCode(1015)
  }

  sealed abstract class InvalidCloseDataException extends RuntimeException
  // scalafix:off Http4sGeneralLinters.leakingSealedHierarchy; bincompat until 1.0
  class InvalidCloseCodeException(val i: Int) extends InvalidCloseDataException
  class ReasonTooLongException(val s: String) extends InvalidCloseDataException
  // scalafix:on

  private def toUnsignedShort(x: Int) = Array[Byte](((x >> 8) & 0xff).toByte, (x & 0xff).toByte)

  private def reasonToBytes(reason: String) = {
    val asBytes = ByteVector.view(reason.getBytes(UTF_8))
    if (asBytes.length > 123)
      Left(new ReasonTooLongException(reason))
    else
      Right(asBytes)
  }

  private def closeCodeToBytes(code: Int): Either[InvalidCloseCodeException, ByteVector] =
    if (code < 1000 || code > 4999) Left(new InvalidCloseCodeException(code))
    else Right(ByteVector.view(toUnsignedShort(code)))

  private def encodeCode(code: Int): ByteVector =
    ByteVector.view(toUnsignedShort(code))

  object Close {
    def apply(code: Int): Either[InvalidCloseDataException, Close] =
      closeCodeToBytes(code).map(Close(_))

    def apply(statusCode: CloseStatusCode): Close =
      new Close(encodeCode(statusCode.code))

    def apply(code: Int, reason: String): Either[InvalidCloseDataException, Close] =
      for {
        c <- closeCodeToBytes(code)
        r <- reasonToBytes(reason)
      } yield Close(c ++ r)

    def apply(
        statusCode: CloseStatusCode,
        reason: String,
    ): Either[InvalidCloseDataException, Close] =
      for {
        r <- reasonToBytes(reason)
        c = encodeCode(statusCode.code)
      } yield new Close(c ++ r)
  }
}
