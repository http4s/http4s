package org.http4s
package server

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.{CountDownLatch, ExecutorService}

import fs2._

trait Server {
  def shutdown: Task[Unit]

  def shutdownNow(): Unit =
    shutdown.unsafeRun

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
