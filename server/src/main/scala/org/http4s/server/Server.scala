package org.http4s
package server

import java.net.{Inet4Address, Inet6Address, InetSocketAddress}
import org.log4s.getLogger

abstract class Server {
  private[this] val logger = getLogger

  def address: InetSocketAddress

  def isSecure: Boolean

  def baseUri: Uri = Uri(
    scheme = Some(if (isSecure) Uri.Scheme.https else Uri.Scheme.http),
    authority = Some(
      Uri.Authority(
        host = address.getAddress match {
          case ipv4: Inet4Address =>
            Uri.Ipv4Address.fromInet4Address(ipv4)
          case ipv6: Inet6Address =>
            Uri.Ipv6Address.fromInet6Address(ipv6)
          case weird =>
            logger.warn(s"Unexpected address type ${weird.getClass}: $weird")
            Uri.RegName(weird.getHostAddress)
        },
        port = Some(address.getPort)
      )),
    path = "/"
  )
}
