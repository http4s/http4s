/*
 * Copyright 2015 http4s.org
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

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => NettyFrame}
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker
import org.http4s.websocket.WebSocketFrame
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.util.Random

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class PerMessageDeflateBench {
  import PerMessageDeflateBench._

  @Param(Array("128", "4096", "65536"))
  var size: Int = _

  @Param(Array("true", "false"))
  var contextTakeover: Boolean = _

  private var params: PerMessageDeflate.Params = _
  private var netty: WebSocketServerExtension = _
  private var messages: Array[Array[Byte]] = _
  private var compressed: Array[Array[Byte]] = _

  @Setup
  def setup(): Unit = {
    params = PerMessageDeflate.Params(!contextTakeover, !contextTakeover, false)
    val offer = new java.util.HashMap[String, String]()
    if (!contextTakeover) {
      offer.put("server_no_context_takeover", null)
      offer.put("client_no_context_takeover", null)
    }
    netty = new PerMessageDeflateServerExtensionHandshaker(6, false, 15, true, true, 0)
      .handshakeExtension(new WebSocketExtensionData("permessage-deflate", offer))

    val random = new Random(42)
    messages = Array.fill(Messages)(json(random, size))
    val encoder = PerMessageDeflate(params, Int.MaxValue).get
    try
      compressed = messages.map { m =>
        encoder.encode(WebSocketFrame.Text(ByteVector.view(m), true))._1.data.toArray
      }
    finally encoder.close()

    val decoded = Array.newBuilder[Array[Byte]]
    nettyDecodeAll(content => decoded += ByteBufUtil.getBytes(content))
    require(
      decoded.result().zip(messages).forall { case (d, m) => d.sameElements(m) },
      "netty failed to decode ember output",
    )
  }

  @Benchmark
  @OperationsPerInvocation(Messages)
  def emberEncode(bh: Blackhole): Unit = {
    val deflate = PerMessageDeflate(params, Int.MaxValue).get
    try {
      var i = 0
      while (i < Messages) {
        bh.consume(deflate.encode(WebSocketFrame.Text(ByteVector.view(messages(i)), true)))
        i += 1
      }
    } finally deflate.close()
  }

  @Benchmark
  @OperationsPerInvocation(Messages)
  def nettyEncode(bh: Blackhole): Unit = {
    val channel = new EmbeddedChannel(netty.newExtensionEncoder())
    try {
      var i = 0
      while (i < Messages) {
        channel.writeOutbound(new TextWebSocketFrame(true, 0, Unpooled.wrappedBuffer(messages(i))))
        val frame = channel.readOutbound[NettyFrame]()
        bh.consume(frame.content().readableBytes())
        frame.release()
        i += 1
      }
    } finally {
      channel.finishAndReleaseAll()
      ()
    }
  }

  @Benchmark
  @OperationsPerInvocation(Messages)
  def emberDecode(bh: Blackhole): Unit = {
    val deflate = PerMessageDeflate(params, Int.MaxValue).get
    try {
      var i = 0
      while (i < Messages) {
        bh.consume(deflate.decode(WebSocketFrame.Text(ByteVector.view(compressed(i)), true), true))
        i += 1
      }
    } finally deflate.close()
  }

  @Benchmark
  @OperationsPerInvocation(Messages)
  def nettyDecode(bh: Blackhole): Unit =
    nettyDecodeAll(content => bh.consume(content.readableBytes()))

  private def nettyDecodeAll(consume: ByteBuf => Unit): Unit = {
    val channel = new EmbeddedChannel(netty.newExtensionDecoder())
    try {
      var i = 0
      while (i < Messages) {
        val in = Unpooled.wrappedBuffer(compressed(i))
        channel.writeInbound(new TextWebSocketFrame(true, WebSocketExtension.RSV1, in))
        val frame = channel.readInbound[NettyFrame]()
        consume(frame.content())
        frame.release()
        i += 1
      }
    } finally {
      channel.finishAndReleaseAll()
      ()
    }
  }
}

object PerMessageDeflateBench {
  final val Messages = 32

  private def json(random: Random, size: Int): Array[Byte] = {
    val sb = new StringBuilder("[")
    while (sb.length < size) {
      sb.append(
        s"""{"id":${random.nextInt(100000)},"user":"user-${random.nextInt(1000)}","""
      )
      sb.append(s""""price":${random.nextInt(10000) / 100.0},"status":"active"},""")
    }
    sb.substring(0, size).getBytes(StandardCharsets.UTF_8)
  }
}
