package org.http4s.client

import java.io.IOException
import java.net.InetSocketAddress

import org.http4s.blaze.util.TickWheelExecutor

import scala.concurrent.duration._

import scalaz.\/
import scalaz.concurrent.Strategy.DefaultExecutorService


package object blaze {

  type AddressResult = \/[IOException, InetSocketAddress]

  // Centralize some defaults
  private[blaze] val DefaultTimeout: Duration = 60.seconds
  private[blaze] val DefaultBufferSize: Int = 8*1024
  private[blaze] def ClientDefaultEC = DefaultExecutorService
  private[blaze] val ClientTickWheel = new TickWheelExecutor()

  /** Default blaze client */
  val defaultClient = SimpleHttp1Client(DefaultTimeout, DefaultBufferSize, ClientDefaultEC, None)
}
