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

import cats.syntax.all._
import fs2.io.net.ProtocolException
import org.http4s.Header
import org.http4s.Headers
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci._
import scodec.bits.ByteVector

import java.util.Locale

/** Per-connection state of the `permessage-deflate` extension.
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc7692 RFC 7692]]
  */
private[internal] final class PerMessageDeflate(
    params: PerMessageDeflate.Params,
    maxFrameSize: Int,
    codec: PerMessageDeflate.Codec,
) {
  import PerMessageDeflate._

  private[this] var compressingOutgoing = false
  private[this] var decompressingIncoming = false

  /** Returns the frame to send and whether its RSV1 bit must be set. */
  def encode(frame: WebSocketFrame): (WebSocketFrame, Boolean) = frame match {
    case _: WebSocketFrame.Text | _: WebSocketFrame.Binary if frame.last && frame.length == 0 =>
      compressingOutgoing = false
      (frame, false)
    case _: WebSocketFrame.Text | _: WebSocketFrame.Binary =>
      compressingOutgoing = !frame.last
      (withData(frame, compress(frame.data, frame.last)), true)
    case _: WebSocketFrame.Continuation if compressingOutgoing =>
      compressingOutgoing = !frame.last
      (withData(frame, compress(frame.data, frame.last)), false)
    case _ =>
      (frame, false)
  }

  def decode(frame: WebSocketFrame, rsv1: Boolean): WebSocketFrame = frame match {
    case _: WebSocketFrame.ControlFrame =>
      if (rsv1) throw new ProtocolException("RSV1 bit set on a control frame")
      frame
    case _: WebSocketFrame.Continuation =>
      if (rsv1) throw new ProtocolException("RSV1 bit set on a continuation frame")
      if (decompressingIncoming) {
        decompressingIncoming = !frame.last
        withData(frame, decompress(frame.data, frame.last))
      } else frame
    case _ if rsv1 =>
      decompressingIncoming = !frame.last
      withData(frame, decompress(frame.data, frame.last))
    case _ =>
      decompressingIncoming = false
      frame
  }

  def close(): Unit = codec.close()

  private def withData(frame: WebSocketFrame, data: ByteVector): WebSocketFrame = frame match {
    case _: WebSocketFrame.Text => WebSocketFrame.Text(data, frame.last)
    case _: WebSocketFrame.Binary => WebSocketFrame.Binary(data, frame.last)
    case _: WebSocketFrame.Continuation => WebSocketFrame.Continuation(data, frame.last)
    case _ => frame
  }

  private def compress(data: ByteVector, last: Boolean): ByteVector = {
    val out = codec.deflate(data)
    if (last) {
      if (params.serverNoContextTakeover) codec.resetDeflater()
      if (out.isEmpty) EmptyBlock
      else if (out.endsWith(Tail)) out.dropRight(Tail.length)
      else out
    } else out
  }

  private def decompress(data: ByteVector, last: Boolean): ByteVector =
    if (last) {
      val out = codec.inflate(data ++ Tail, maxFrameSize)
      if (params.clientNoContextTakeover) codec.resetInflater()
      out
    } else codec.inflate(data, maxFrameSize)
}

private[internal] object PerMessageDeflate extends PerMessageDeflatePlatform {

  /** A stateful raw DEFLATE compressor and decompressor pair. Not thread-safe. */
  trait Codec {

    /** Compresses `data` and finishes with a sync flush. */
    def deflate(data: ByteVector): ByteVector
    def resetDeflater(): Unit

    /** Decompresses `data`, failing if the output exceeds `maxSize` bytes. */
    def inflate(data: ByteVector, maxSize: Int): ByteVector
    def resetInflater(): Unit

    def close(): Unit
  }

  /** The agreed parameters of an accepted offer. */
  final case class Params(
      serverNoContextTakeover: Boolean,
      clientNoContextTakeover: Boolean,
      serverMaxWindowBits: Option[Int],
  ) {
    def toHeader: Header.Raw = {
      val params = List(
        Some(ExtensionName),
        Some(ServerNoContextTakeover).filter(_ => serverNoContextTakeover),
        Some(ClientNoContextTakeover).filter(_ => clientNoContextTakeover),
        serverMaxWindowBits.map(bits => s"$ServerMaxWindowBits=$bits"),
      )
      Header.Raw(HeaderName, params.flatten.mkString("; "))
    }
  }

  val HeaderName: CIString = ci"Sec-WebSocket-Extensions"

  private[internal] val Rsv1 = 0x40

  private val ExtensionName = "permessage-deflate"
  private val ServerNoContextTakeover = "server_no_context_takeover"
  private val ClientNoContextTakeover = "client_no_context_takeover"
  private val ServerMaxWindowBits = "server_max_window_bits"
  private val ClientMaxWindowBits = "client_max_window_bits"
  private val MaxWindowBits = 15

  private val Tail = ByteVector(0x00, 0x00, 0xff, 0xff)
  private val EmptyBlock = ByteVector(0x00)

  def apply(params: Params, maxFrameSize: Int): PerMessageDeflate =
    new PerMessageDeflate(params, maxFrameSize, createCodec())

  /** Accepts the first valid `permessage-deflate` offer of the request, if any. */
  def negotiate(headers: Headers): Option[Params] =
    if (!isSupported) None
    else
      headers.get(HeaderName).flatMap { values =>
        parseOffers(values.toList.map(_.value).mkString(",")).collectFirstSome {
          case (name, params) if name.equalsIgnoreCase(ExtensionName) => accept(params)
          case _ => None
        }
      }

  private def accept(params: List[(String, Option[String])]): Option[Params] = {
    val names = params.map(_._1)
    if (names.distinct.size != names.size) None
    else
      params.foldLeftM(Params(false, false, None)) {
        case (agreed, (ServerNoContextTakeover, None)) =>
          Some(agreed.copy(serverNoContextTakeover = true))
        case (agreed, (ClientNoContextTakeover, None)) =>
          Some(agreed.copy(clientNoContextTakeover = true))
        case (agreed, (ServerMaxWindowBits, Some(value))) =>
          windowBits(value)
            .filter(_ == MaxWindowBits)
            .map(bits => agreed.copy(serverMaxWindowBits = Some(bits)))
        case (agreed, (ClientMaxWindowBits, None)) =>
          Some(agreed)
        case (agreed, (ClientMaxWindowBits, Some(value))) =>
          windowBits(value).as(agreed)
        case _ =>
          None
      }
  }

  private def windowBits(value: String): Option[Int] =
    if (value.matches("[1-9][0-9]?")) Some(value.toInt).filter(bits => bits >= 8 && bits <= 15)
    else None

  private def parseOffers(value: String): List[(String, List[(String, Option[String])])] =
    split(value, ',').filter(_.nonEmpty).map { offer =>
      val segments = split(offer, ';')
      val params = segments.tail.filter(_.nonEmpty).map { param =>
        param.indexOf('=') match {
          case -1 => (param.toLowerCase(Locale.ROOT), None)
          case i =>
            val name = param.substring(0, i).trim.toLowerCase(Locale.ROOT)
            (name, Some(unquote(param.substring(i + 1).trim)))
        }
      }
      (segments.head, params)
    }

  private def split(value: String, separator: Char): List[String] = {
    val parts = List.newBuilder[String]
    val part = new StringBuilder
    var quoted = false
    value.foreach { c =>
      if (c == '"') quoted = !quoted
      if (c == separator && !quoted) {
        parts += part.result().trim
        part.clear()
      } else part.append(c)
    }
    parts += part.result().trim
    parts.result()
  }

  private def unquote(value: String): String =
    if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\""))
      value.substring(1, value.length - 1)
    else value
}
