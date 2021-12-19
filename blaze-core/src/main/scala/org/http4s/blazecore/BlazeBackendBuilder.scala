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
package blazecore

import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.channel.OptionValue

import java.net.SocketOption
import java.net.StandardSocketOptions

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
      ChannelOptions(channelOptions.options.filterNot(_.key == key) :+ OptionValue(key, value))
    )
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
