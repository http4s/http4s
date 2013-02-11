package org.http4s

import java.net.InetAddress

case class HttpIp(ip: InetAddress) {
  def value: String = ip.getHostAddress
  override def toString = value
}

object HttpIp {
  implicit def apply(s: String): HttpIp = InetAddress.getByName(s)
  implicit def fromInetAddress(a: InetAddress): HttpIp = apply(a)
}