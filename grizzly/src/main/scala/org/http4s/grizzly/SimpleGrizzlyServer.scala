package org.http4s
package grizzly

import org.glassfish.grizzly.http.server.{Response, HttpHandler, NetworkListener, HttpServer}
import org.glassfish.grizzly.threadpool.ThreadPoolConfig
import concurrent.ExecutionContext

/**
 * @author Bryce Anderson
 * Created on 2/10/13 at 3:06 PM
 */

object SimpleGrizzlyServer {
  def apply(port: Int = 8080, serverRoot:String = "/*")(route: Route)(implicit executionContext: ExecutionContext = ExecutionContext.global) =
  new SimpleGrizzlyServer(port = port, serverRoot = serverRoot)(Seq(route))
}

class SimpleGrizzlyServer(port: Int=8080,
                          address: String = "0.0.0.0",
                          serverRoot:String = "/*",
                          serverName:String="simple-grizzly-server",
                          corePoolSize:Int = 4,
                          maxPoolSize:Int = 10)(routes:Seq[Route])(implicit executionContext: ExecutionContext = ExecutionContext.global)
{
  val http4sServlet = new Http4sGrizzly(routes reduce (_ orElse _))(executionContext)
  val httpServer = new HttpServer
  val networkListener = new NetworkListener(serverName, address, port)

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
