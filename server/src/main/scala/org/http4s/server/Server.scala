package org.http4s
package server

import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

import cats.effect._

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
