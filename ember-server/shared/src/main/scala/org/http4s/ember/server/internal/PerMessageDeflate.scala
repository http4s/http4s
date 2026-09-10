/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import cats.parse.Parser
import cats.syntax.all._
import fs2.io.net.ProtocolException
import org.http4s.Header
import org.http4s.Headers
import org.http4s.internal.parsing.CommonRules._
import org.http4s.websocket.FrameTranscoder
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci._
import scodec.bits.ByteVector

import java.nio.ByteBuffer
import java.util.Locale

/** Per-connection state of the `permessage-deflate` WebSocket extension.
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc7692 RFC 7692]]
  */
private[internal] final class PerMessageDeflate(
    params: PerMessageDeflate.Params,
    maxMessageSize: Int,
    codec: PerMessageDeflate.Codec,
) {
  import PerMessageDeflate._

  private[this] var deflating = false
  private[this] var inflating = false
  private[this] var inflatedBytes = 0

  /** Compresses data frames and sets RSV1 on the first frame of each message. */
  def encode(frame: WebSocketFrame, transcoder: FrameTranscoder): Array[ByteBuffer] = {
    val (compress, first) = frame match {
      case _: WebSocketFrame.Text | _: WebSocketFrame.Binary =>
        (!(frame.last && frame.length == 0), true)
      case _: WebSocketFrame.Continuation => (deflating, false)
      case _ => (false, false)
    }
    if (!compress) transcoder.frameToBuffer(frame)
    else {
      deflating = !frame.last
      val buffers = transcoder.frameToBuffer(withData(frame, deflate(frame)))
      if (first) {
        buffers(0).put(0, (buffers(0).get(0) | Rsv1).toByte)
        ()
      }
      buffers
    }
  }

  /** Decompresses data frames of messages that were sent with RSV1. */
  def decode(frame: WebSocketFrame, rsv1: Boolean): WebSocketFrame = frame match {
    case _: WebSocketFrame.ControlFrame | _: WebSocketFrame.Continuation if rsv1 =>
      throw new ProtocolException("RSV1 set on a control or continuation frame")
    case _: WebSocketFrame.ControlFrame => frame
    case _: WebSocketFrame.Continuation => if (inflating) inflate(frame) else frame
    case _ if inflating =>
      throw new ProtocolException("New message before the compressed message finished")
    case _ if rsv1 =>
      inflatedBytes = 0
      inflate(frame)
    case _ => frame
  }

  def close(): Unit = codec.close()

  private def deflate(frame: WebSocketFrame): ByteVector = {
    val out = codec.deflate(frame.data)
    if (!frame.last) out
    else {
      if (params.serverNoContextTakeover) codec.endDeflater()
      if (out.isEmpty) EmptyBlock else out.dropRight(Tail.length)
    }
  }

  private def inflate(frame: WebSocketFrame): WebSocketFrame = {
    inflating = !frame.last
    val data = if (frame.last) frame.data ++ Tail else frame.data
    val out = codec.inflate(data, maxMessageSize - inflatedBytes)
    inflatedBytes += out.length.toInt
    if (frame.last && params.clientNoContextTakeover) codec.resetInflater()
    withData(frame, out)
  }

  private def withData(frame: WebSocketFrame, data: ByteVector): WebSocketFrame = frame match {
    case _: WebSocketFrame.Text => WebSocketFrame.Text(data, frame.last)
    case _: WebSocketFrame.Binary => WebSocketFrame.Binary(data, frame.last)
    case _ => WebSocketFrame.Continuation(data, frame.last)
  }
}

private[internal] object PerMessageDeflate extends PerMessageDeflatePlatform {

  /** Raw DEFLATE compressor and decompressor, both created on first use. Not thread-safe. */
  trait Codec {

    /** Compresses `data` and ends with a sync flush. */
    def deflate(data: ByteVector): ByteVector

    /** Frees the compressor, so the next message starts with an empty window. */
    def endDeflater(): Unit

    /** Decompresses `data`, failing if the output exceeds `maxSize` bytes. */
    def inflate(data: ByteVector, maxSize: Int): ByteVector

    /** Empties the decompressor's window, keeping it allocated. */
    def resetInflater(): Unit

    def close(): Unit
  }

  final case class Params(
      serverNoContextTakeover: Boolean,
      clientNoContextTakeover: Boolean,
      serverMaxWindowBits: Boolean,
  ) {
    def toHeader: Header.Raw = {
      val params = List(
        ServerNoContextTakeover -> serverNoContextTakeover,
        ClientNoContextTakeover -> clientNoContextTakeover,
        s"$ServerMaxWindowBits=15" -> serverMaxWindowBits,
      ).collect { case (agreed, true) => agreed }
      Header.Raw(HeaderName, (ExtensionName :: params).mkString("; "))
    }
  }

  val HeaderName: CIString = ci"Sec-WebSocket-Extensions"

  private val ExtensionName = "permessage-deflate"
  private val ServerNoContextTakeover = "server_no_context_takeover"
  private val ClientNoContextTakeover = "client_no_context_takeover"
  private val ServerMaxWindowBits = "server_max_window_bits"
  private val ClientMaxWindowBits = "client_max_window_bits"

  private val Rsv1 = 0x40
  private val Tail = ByteVector(0x00, 0x00, 0xff, 0xff)
  private val EmptyBlock = ByteVector(0x00)

  def rsv1(buffer: ByteBuffer): Boolean = (buffer.get(0) & Rsv1) != 0

  /** Returns `None` on platforms without a DEFLATE implementation. */
  def apply(params: Params, maxMessageSize: Int): Option[PerMessageDeflate] =
    codecFactory.map(codec => new PerMessageDeflate(params, maxMessageSize, codec()))

  /** Accepts the first valid `permessage-deflate` offer, if the platform supports it. */
  def negotiate(headers: Headers): Option[Params] =
    headers.get(HeaderName).filter(_ => codecFactory.isDefined).flatMap { values =>
      offers.parseAll(values.toList.map(_.value).mkString(",")).toOption.flatMap {
        _.collectFirstSome {
          case (name, params) if name.equalsIgnoreCase(ExtensionName) => accept(params)
          case _ => None
        }
      }
    }

  private def accept(params: List[(String, Option[String])]): Option[Params] =
    if (params.map(_._1).distinct.size != params.size) None
    else
      params.foldLeftM(Params(false, false, false)) {
        case (agreed, (ServerNoContextTakeover, None)) =>
          Some(agreed.copy(serverNoContextTakeover = true))
        case (agreed, (ClientNoContextTakeover, None)) =>
          Some(agreed.copy(clientNoContextTakeover = true))
        case (agreed, (ServerMaxWindowBits, Some("15"))) =>
          Some(agreed.copy(serverMaxWindowBits = true))
        case (agreed, (ClientMaxWindowBits, None)) =>
          Some(agreed)
        case (agreed, (ClientMaxWindowBits, Some(bits))) if bits.matches("[89]|1[0-5]") =>
          Some(agreed)
        case _ =>
          None
      }

  private def isText(c: Char): Boolean =
    c != ',' && c != ';' && c != '=' && c != '"' && !c.isWhitespace

  private val text: Parser[String] = Parser.charsWhile(isText)

  private val param: Parser[(String, Option[String])] = {
    val value = ows.with1 *> Parser.char('=') *> ows *> quotedString.orElse(
      Parser.charsWhile0(isText)
    )
    (text ~ value.backtrack.?).map { case (name, v) => (name.toLowerCase(Locale.ROOT), v) }
  }

  private val offers = headerRep1(
    text ~ (ows.with1 *> Parser.char(';') *> ows *> param.?).backtrack.rep0.map(_.flatten)
  )
}
