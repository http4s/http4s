/*
 * Copyright 2014 http4s.org
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
package blaze
package client

import cats.effect.IO
import org.http4s.blaze.channel.ChannelOptions

class BlazeClientBuilderSuite extends Http4sSuite {
  private def builder = BlazeClientBuilder[IO]

  test("default to empty") {
    assertEquals(builder.channelOptions, ChannelOptions(Vector.empty))
  }

  test("set socket send buffer size") {
    assertEquals(builder.withSocketSendBufferSize(8192).socketSendBufferSize, Some(8192))
  }

  test("set socket receive buffer size") {
    assertEquals(builder.withSocketReceiveBufferSize(8192).socketReceiveBufferSize, Some(8192))
  }

  test("set socket keepalive") {
    assertEquals(builder.withSocketKeepAlive(true).socketKeepAlive, Some(true))
  }

  test("set socket reuse address") {
    assertEquals(builder.withSocketReuseAddress(true).socketReuseAddress, Some(true))
  }

  test("set TCP nodelay") {
    assertEquals(builder.withTcpNoDelay(true).tcpNoDelay, Some(true))
  }

  test("unset socket send buffer size") {
    assertEquals(
      builder
        .withSocketSendBufferSize(8192)
        .withDefaultSocketSendBufferSize
        .socketSendBufferSize,
      None,
    )
  }

  test("unset socket receive buffer size") {
    assertEquals(
      builder
        .withSocketReceiveBufferSize(8192)
        .withDefaultSocketReceiveBufferSize
        .socketReceiveBufferSize,
      None,
    )
  }

  test("unset socket keepalive") {
    assertEquals(builder.withSocketKeepAlive(true).withDefaultSocketKeepAlive.socketKeepAlive, None)
  }

  test("unset socket reuse address") {
    assertEquals(
      builder
        .withSocketReuseAddress(true)
        .withDefaultSocketReuseAddress
        .socketReuseAddress,
      None,
    )
  }

  test("unset TCP nodelay") {
    assertEquals(builder.withTcpNoDelay(true).withDefaultTcpNoDelay.tcpNoDelay, None)
  }

  test("overwrite keys") {
    assertEquals(
      builder
        .withSocketSendBufferSize(8192)
        .withSocketSendBufferSize(4096)
        .socketSendBufferSize,
      Some(4096),
    )
  }

  test("set header max length") {
    assertEquals(builder.withMaxHeaderLength(64).maxHeaderLength, 64)
  }
}
