package org.http4s.client

import java.util.concurrent.TimeUnit
import java.util.concurrent._

import org.http4s.BuildInfo
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.blaze.util.TickWheelExecutor

import scala.concurrent.duration._


package object blaze {

  // Centralize some defaults
  private[blaze] val DefaultTimeout: Duration = 60.seconds
  private[blaze] val DefaultBufferSize: Int = 8*1024
  private[blaze] val DefaultUserAgent = Some(`User-Agent`(AgentProduct("http4s-blaze", Some(BuildInfo.version))))
  private[blaze] val ClientDefaultEC = {
    val threadFactory = new ThreadFactory {
      val defaultThreadFactory = Executors.defaultThreadFactory()
      def newThread(r: Runnable): Thread = {
        val t = defaultThreadFactory.newThread(r)
        t.setDaemon(true)
        t
      }
    }

    new ThreadPoolExecutor(
      2,
      Runtime.getRuntime.availableProcessors() * 6,
      60L, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](),
      threadFactory
    )
  }

  private[blaze] val ClientTickWheel = new TickWheelExecutor()

  /** Default blaze client */
  val defaultClient = SimpleHttp1Client(timeout = DefaultTimeout,
                                   maxRedirects = 0,
                                     bufferSize = DefaultBufferSize,
                                       executor = ClientDefaultEC,
                                     sslContext = None)
}
