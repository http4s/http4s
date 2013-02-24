package org.http4s
package grizzly

import org.glassfish.grizzly.http.server.{NetworkListener, HttpServer}
import org.glassfish.grizzly.threadpool.ThreadPoolConfig
import concurrent.ExecutionContext

/**
 * @author Bryce Anderson
 * Created on 2/10/13 at 3:06 PM
 */

object SimpleGrizzlyServer {
  def apply(port: Int = 8080, address: String ="0.0.0.0", serverRoot:String = "/*", chunkSize: Int = 32 * 1024)(route: Route)(implicit executionContext: ExecutionContext = concurrent.ExecutionContext.fromExecutorService(java.util.concurrent.Executors.newCachedThreadPool())) =
  new SimpleGrizzlyServer(port = port, address = address, serverRoot = serverRoot, chunkSize = chunkSize)(Seq(route))
}

class SimpleGrizzlyServer(port: Int=8080,
                          address: String = "0.0.0.0",
                          serverRoot:String = "/*",
                          serverName:String="simple-grizzly-server",
                          chunkSize: Int = 32 * 1024,
                          corePoolSize:Int = 10,
                          maxPoolSize:Int = 20,
                          maxReqHeaders: Int = -1,
                           maxHeaderSize: Int = -1)(routes:Seq[Route])(implicit executionContext: ExecutionContext = ExecutionContext.global)
{
  val http4sServlet = new Http4sGrizzly(routes reduce (_ orElse _), chunkSize)(executionContext)
  val httpServer = new HttpServer
  val networkListener = new NetworkListener(serverName, address, port)
  // For preventing DoS attacks
  if (maxHeaderSize > 0) networkListener.setMaxHttpHeaderSize(maxHeaderSize)
  if (maxReqHeaders > 0) networkListener.setMaxRequestHeaders(maxReqHeaders)

  val threadPoolConfig = ThreadPoolConfig
    .defaultConfig()
    .setCorePoolSize(corePoolSize)
    .setMaxPoolSize(maxPoolSize)

  networkListener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig)

  httpServer.addListener(networkListener)


  httpServer.getServerConfiguration().addHttpHandler(http4sServlet,serverRoot)

  try {
    httpServer.start()
    Thread.currentThread().join()

  } catch  {
    case e: Throwable => println(e)
  }
}
