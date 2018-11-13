package org.http4s
package blazecore

import java.net.{SocketOption, StandardSocketOptions}
import org.http4s.blaze.channel.{ChannelOptions, OptionValue}

private[http4s] trait BlazeBackendBuilder[B] {
  type Self

  def channelOptions: ChannelOptions

  def channelOption[A](socketOption: SocketOption[A]) =
    channelOptions.options.collectFirst {
      case OptionValue(key, value) if key == socketOption =>
        value.asInstanceOf[A]
    }
  def withChannelOptions(channelOptions: ChannelOptions): Self
  def withChannelOption[A](key: SocketOption[A], value: A): Self =
    withChannelOptions(
      ChannelOptions(channelOptions.options.filterNot(_.key == key) :+ OptionValue(key, value)))
  def withDefaultChannelOption[A](key: SocketOption[A]): Self =
    withChannelOptions(ChannelOptions(channelOptions.options.filterNot(_.key == key)))

  def socketSendBufferSize: Option[Int] =
    channelOption(StandardSocketOptions.SO_SNDBUF).map(Int.unbox)
  def withSocketSendBufferSize(socketSendBufferSize: Int): Self =
    withChannelOption(StandardSocketOptions.SO_SNDBUF, Int.box(socketSendBufferSize))
  def withDefaultSocketSendBufferSize: Self =
    withDefaultChannelOption(StandardSocketOptions.SO_SNDBUF)

  def socketReceiveBufferSize: Option[Int] =
    channelOption(StandardSocketOptions.SO_RCVBUF).map(Int.unbox)
  def withSocketReceiveBufferSize(socketReceiveBufferSize: Int): Self =
    withChannelOption(StandardSocketOptions.SO_RCVBUF, Int.box(socketReceiveBufferSize))
  def withDefaultSocketReceiveBufferSize: Self =
    withDefaultChannelOption(StandardSocketOptions.SO_RCVBUF)

  def socketKeepAlive: Option[Boolean] =
    channelOption(StandardSocketOptions.SO_KEEPALIVE).map(Boolean.unbox)
  def withSocketKeepAlive(socketKeepAlive: Boolean): Self =
    withChannelOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.box(socketKeepAlive))
  def withDefaultSocketKeepAlive: Self =
    withDefaultChannelOption(StandardSocketOptions.SO_KEEPALIVE)

  def socketReuseAddress: Option[Boolean] =
    channelOption(StandardSocketOptions.SO_REUSEADDR).map(Boolean.unbox)
  def withSocketReuseAddress(socketReuseAddress: Boolean): Self =
    withChannelOption(StandardSocketOptions.SO_REUSEADDR, Boolean.box(socketReuseAddress))
  def withDefaultSocketReuseAddress: Self =
    withDefaultChannelOption(StandardSocketOptions.SO_REUSEADDR)

  def tcpNoDelay: Option[Boolean] =
    channelOption(StandardSocketOptions.TCP_NODELAY).map(Boolean.unbox)
  def withTcpNoDelay(tcpNoDelay: Boolean): Self =
    withChannelOption(StandardSocketOptions.TCP_NODELAY, Boolean.box(tcpNoDelay))
  def withDefaultTcpNoDelay: Self =
    withDefaultChannelOption(StandardSocketOptions.TCP_NODELAY)
}
