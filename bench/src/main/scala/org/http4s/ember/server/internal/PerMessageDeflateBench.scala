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

// sbt "bench/Jmh/run -i 5 -wi 3 -f 1 -t 1 org.http4s.ember.server.internal.PerMessageDeflateBench"
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
    val encoder = PerMessageDeflate(params, Int.MaxValue)
    try
      compressed = messages.map { m =>
        encoder.encode(WebSocketFrame.Text(ByteVector.view(m), true))._1.data.toArray
      }
    finally encoder.close()

    val decoded = nettyDecodeAll(_ => ())
    require(decoded.sameElements(messages.map(_.length)), "netty failed to decode ember output")
  }

  @Benchmark
  @OperationsPerInvocation(Messages)
  def emberEncode(bh: Blackhole): Unit = {
    val deflate = PerMessageDeflate(params, Int.MaxValue)
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
    val deflate = PerMessageDeflate(params, Int.MaxValue)
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
  def nettyDecode(bh: Blackhole): Unit = {
    nettyDecodeAll(bh.consume)
    ()
  }

  private def nettyDecodeAll(consume: Int => Unit): Array[Int] = {
    val channel = new EmbeddedChannel(netty.newExtensionDecoder())
    try {
      val sizes = new Array[Int](Messages)
      var i = 0
      while (i < Messages) {
        val in = Unpooled.wrappedBuffer(compressed(i))
        channel.writeInbound(new TextWebSocketFrame(true, WebSocketExtension.RSV1, in))
        val frame = channel.readInbound[NettyFrame]()
        sizes(i) = frame.content().readableBytes()
        consume(sizes(i))
        frame.release()
        i += 1
      }
      sizes
    } finally {
      channel.finishAndReleaseAll()
      ()
    }
  }
}

/*
Netty 4.2.19, JDK 25, ns per message:
[info] Benchmark                           (contextTakeover)  (size)  Mode  Cnt       Score       Error  Units
[info] PerMessageDeflateBench.emberDecode               true     128  avgt    5     274.931 ±    19.513  ns/op
[info] PerMessageDeflateBench.emberDecode               true    4096  avgt    5    4143.080 ±    42.768  ns/op
[info] PerMessageDeflateBench.emberDecode               true   65536  avgt    5   78328.403 ±  6283.842  ns/op
[info] PerMessageDeflateBench.emberDecode              false     128  avgt    5    1283.573 ±    19.609  ns/op
[info] PerMessageDeflateBench.emberDecode              false    4096  avgt    5    6102.367 ±   124.266  ns/op
[info] PerMessageDeflateBench.emberDecode              false   65536  avgt    5   80870.974 ±  2286.750  ns/op
[info] PerMessageDeflateBench.emberEncode               true     128  avgt    5    1977.860 ±    28.623  ns/op
[info] PerMessageDeflateBench.emberEncode               true    4096  avgt    5   35564.003 ±  1234.601  ns/op
[info] PerMessageDeflateBench.emberEncode               true   65536  avgt    5  598135.878 ±  6945.568  ns/op
[info] PerMessageDeflateBench.emberEncode              false     128  avgt    5    3965.264 ±   103.044  ns/op
[info] PerMessageDeflateBench.emberEncode              false    4096  avgt    5   27772.723 ±  1513.760  ns/op
[info] PerMessageDeflateBench.emberEncode              false   65536  avgt    5  551393.505 ±  8905.741  ns/op
[info] PerMessageDeflateBench.nettyDecode               true     128  avgt    5     854.403 ±    15.260  ns/op
[info] PerMessageDeflateBench.nettyDecode               true    4096  avgt    5    4862.180 ±   240.164  ns/op
[info] PerMessageDeflateBench.nettyDecode               true   65536  avgt    5   63989.693 ±  2939.976  ns/op
[info] PerMessageDeflateBench.nettyDecode              false     128  avgt    5    1674.761 ±    37.545  ns/op
[info] PerMessageDeflateBench.nettyDecode              false    4096  avgt    5    6187.867 ±   123.962  ns/op
[info] PerMessageDeflateBench.nettyDecode              false   65536  avgt    5   63768.100 ±  3932.821  ns/op
[info] PerMessageDeflateBench.nettyEncode               true     128  avgt    5    2557.748 ±    73.793  ns/op
[info] PerMessageDeflateBench.nettyEncode               true    4096  avgt    5   37019.886 ±  4489.217  ns/op
[info] PerMessageDeflateBench.nettyEncode               true   65536  avgt    5  596818.456 ± 18257.976  ns/op
[info] PerMessageDeflateBench.nettyEncode              false     128  avgt    5    5769.133 ±   115.478  ns/op
[info] PerMessageDeflateBench.nettyEncode              false    4096  avgt    5   29756.928 ±  2454.866  ns/op
[info] PerMessageDeflateBench.nettyEncode              false   65536  avgt    5  552465.976 ± 12299.813  ns/op
 */

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
