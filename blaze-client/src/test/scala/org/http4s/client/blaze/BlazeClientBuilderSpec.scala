package org.http4s
package client
package blaze

import cats.effect.IO
import org.http4s.blaze.channel.ChannelOptions

class BlazeClientBuilderSpec extends Http4sSpec {
  def builder = BlazeClientBuilder[IO](testExecutionContext)

  "ChannelOptions" should {
    "default to empty" in {
      builder.channelOptions must_== ChannelOptions(Vector.empty)
    }
    "set socket send buffer size" in {
      builder.withSocketSendBufferSize(8192).socketSendBufferSize must beSome(8192)
    }
    "set socket receive buffer size" in {
      builder.withSocketReceiveBufferSize(8192).socketReceiveBufferSize must beSome(8192)
    }
    "set socket keepalive" in {
      builder.withSocketKeepAlive(true).socketKeepAlive must beSome(true)
    }
    "set socket reuse address" in {
      builder.withSocketReuseAddress(true).socketReuseAddress must beSome(true)
    }
    "set TCP nodelay" in {
      builder.withTcpNoDelay(true).tcpNoDelay must beSome(true)
    }
    "unset socket send buffer size" in {
      builder
        .withSocketSendBufferSize(8192)
        .withDefaultSocketSendBufferSize
        .socketSendBufferSize must beNone
    }
    "unset socket receive buffer size" in {
      builder
        .withSocketReceiveBufferSize(8192)
        .withDefaultSocketReceiveBufferSize
        .socketReceiveBufferSize must beNone
    }
    "unset socket keepalive" in {
      builder.withSocketKeepAlive(true).withDefaultSocketKeepAlive.socketKeepAlive must beNone
    }
    "unset socket reuse address" in {
      builder
        .withSocketReuseAddress(true)
        .withDefaultSocketReuseAddress
        .socketReuseAddress must beNone
    }
    "unset TCP nodelay" in {
      builder.withTcpNoDelay(true).withDefaultTcpNoDelay.tcpNoDelay must beNone
    }
    "overwrite keys" in {
      builder
        .withSocketSendBufferSize(8192)
        .withSocketSendBufferSize(4096)
        .socketSendBufferSize must beSome(4096)
    }
  }

  "Header options" should {
    "set header max length" in {
      builder.withMaxHeaderLength(64).maxHeaderLength must_== 64
    }
  }
}
