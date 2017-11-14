package org.http4s
package server

import cats.effect._
import java.net.{Inet4Address, Inet6Address, InetSocketAddress}
import java.util.concurrent.CountDownLatch
import org.log4s.getLogger

abstract class Server[F[_]](implicit F: Effect[F]) {
  def shutdown: F[Unit]

  def shutdownNow(): Unit =
    F.runAsync(shutdown)(_ => IO.unit).unsafeRunSync()

  @deprecated("Compose with the shutdown task instead.", "0.14")
  def onShutdown(f: => Unit): this.type

  def address: InetSocketAddress

  /**
    * Blocks until the server shuts down.
    */
  @deprecated("Use ServerApp instead.", "0.14")
  def awaitShutdown(): Unit = {
    val latch = new CountDownLatch(1)
    onShutdown(latch.countDown())
    latch.await()
  }
}

/** Temporary function so we don't break MiMa.  In 0.18, this moves back to the Server trait. */
private[http4s] object Server {
  private[this] val logger = getLogger

  def baseUri(address: InetSocketAddress, isSecure: Boolean): Uri = Uri(
    scheme = Some(if (isSecure) Uri.Scheme.https else Uri.Scheme.http),
    authority = Some(
      Uri.Authority(
        host = address.getAddress match {
          case ipv4: Inet4Address =>
            Uri.IPv4(ipv4.getHostAddress)
          case ipv6: Inet6Address =>
            Uri.IPv6(ipv6.getHostAddress)
          case weird =>
            logger.warn(s"Unexpected address ftype ${weird.getClass}: $weird")
            Uri.RegName(weird.getHostAddress)
        },
        port = Some(address.getPort)
      )),
    path = "/"
  )
}
