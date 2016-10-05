package org.http4s
package server

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.{CountDownLatch, ExecutorService}

import scalaz._, Scalaz._
import scalaz.concurrent.Task

trait Server {
  private[this] val latch = new CountDownLatch(1)

  /** A task that triggers a server shutdown when run */
  final def shutdown: Task[Unit] =
    destroy >> Task.delay(latch.countDown())

  final def shutdownNow(): Unit =
    shutdown.run

  /** Blocks until the server shuts down. */
  final def awaitShutdown(): Unit = {
    println("Waiting")
    latch.await()
    println("Done waiting")
  }

  protected def destroy: Task[Unit]

  @deprecated("Compose with the shutdown task instead.", "0.14")
  def onShutdown(f: => Unit): this.type

  def address: InetSocketAddress
}
