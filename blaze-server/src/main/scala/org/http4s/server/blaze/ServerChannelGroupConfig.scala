package org.http4s
package server
package blaze

import org.http4s.blaze.channel.ServerChannelGroup
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import scalaz.concurrent.Task

abstract class ServerChannelGroupConfig {
  def start: Task[ServerChannelGroup]
}

private[blaze] abstract class Nio1ServerChannelGroupConfigBase extends ServerChannelGroupConfig {
  self: Nio1ServerChannelGroupConfig =>

  def start: Task[ServerChannelGroup] =
    Task.delay(NIO1SocketServerGroup.fixedGroup(connectorPoolSize, bufferSize))
}

private[blaze] abstract class Nio2ServerChannelGroupConfigBase extends ServerChannelGroupConfig {
  self: Nio2ServerChannelGroupConfig =>

  def start: Task[ServerChannelGroup] =
    Task.delay(NIO2SocketServerGroup.fixedGroup(connectorPoolSize, bufferSize))
}
