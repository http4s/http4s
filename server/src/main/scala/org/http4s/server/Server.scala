package org.http4s
package server

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.{CountDownLatch, ExecutorService}

import scalaz.concurrent.Task

trait Server {
  def shutdown: Task[this.type]

  def shutdownNow(): this.type =
    shutdown.unsafePerformSync

  def onShutdown(f: => Unit): this.type

  /**
   * Blocks until the server shuts down.
   */
  def awaitShutdown(): Unit = {
    val latch = new CountDownLatch(1)
    onShutdown(latch.countDown())
    latch.await()
  }
}


