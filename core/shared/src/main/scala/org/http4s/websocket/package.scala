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

import fs2.io.net.ProtocolException
import scodec.bits.ByteVector

import scala.annotation.switch

package object websocket {
  // Masks for extracting fields
  private[websocket] val OP_CODE = 0xf
  private[websocket] val FINISHED = 0x80
  private[websocket] val MASK = 0x80
  private[websocket] val LENGTH = 0x7f
  private[websocket] val RESERVED = 0xe

  // message op codes
  private[websocket] val CONTINUATION = 0x0
  private[websocket] val TEXT = 0x1
  private[websocket] val BINARY = 0x2
  private[websocket] val CLOSE = 0x8
  private[websocket] val PING = 0x9
  private[websocket] val PONG = 0xa

  // Type constructors
  @throws[ProtocolException]
  private[websocket] def makeFrame(opcode: Int, data: ByteVector, last: Boolean): WebSocketFrame =
    (opcode: @switch) match {
      case 0x0 => WebSocketFrame.Continuation(data, last)
      case 0x1 => WebSocketFrame.Text(data, last)
      case 0x2 => WebSocketFrame.Binary(data, last)
      case 0x3 | 0x4 | 0x5 | 0x6 | 0x7 =>
        // Reserved for future non-control frames.
        throw new ReservedOpcodeException(opcode)
      case 0x8 =>
        if (data.length == 1)
          throw new ProtocolException("Close frame must have 0 data bits or at least 2")
        if (!last) throw new ProtocolException("Control frame cannot be fragmented: Close")
        WebSocketFrame.Close(data)
      case 0x9 =>
        if (!last) throw new ProtocolException("Control frame cannot be fragmented: Ping")
        else WebSocketFrame.Ping(data)
      case 0xa =>
        if (!last) throw new ProtocolException("Control frame cannot be fragmented: Pong")
        else WebSocketFrame.Pong(data)
      case 0xb | 0xc | 0xd | 0xe | 0xf =>
        // Reserved for future control frames.
        throw new ReservedOpcodeException(opcode)
      case _ =>
        throw new UnknownOpcodeException(opcode)
    }
}
