package org.http4s
package server

import java.net.{Inet4Address, Inet6Address, InetSocketAddress}
import org.log4s.getLogger

abstract class Server[F[_]] {
  private[this] val logger = getLogger

  def address: InetSocketAddress

  def isSecure: Boolean

  def baseUri: Uri = Uri(
    scheme = Some(if (isSecure) Uri.Scheme.https else Uri.Scheme.http),
    authority = Some(
      Uri.Authority(
        host = address.getAddress match {
          case ipv4: Inet4Address =>
            Uri.IPv4(ipv4.getHostAddress)
          case ipv6: Inet6Address =>
            Uri.IPv6(ipv6.getHostAddress)
          case weird =>
            logger.warn(s"Unexpected address type ${weird.getClass}: $weird")
            Uri.RegName(weird.getHostAddress)
        },
        port = Some(address.getPort)
      )),
    path = "/"
  )
}
