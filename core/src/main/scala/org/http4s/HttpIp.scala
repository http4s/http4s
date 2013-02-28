package org.http4s

import java.net.InetAddress

case class HttpIp(ip: InetAddress) {
  def value: String = ip.getHostAddress

  def hostAddress: String = ip.getHostAddress
  def hostName: String = ip.getHostName

  override def toString = value
}

object HttpIp {
  implicit def apply(s: String): HttpIp = InetAddress.getByName(s)
  implicit def fromInetAddress(a: InetAddress): HttpIp = apply(a)

  def localhost: HttpIp = apply(InetAddress.getLocalHost)
}