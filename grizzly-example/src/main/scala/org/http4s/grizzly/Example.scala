package org.http4s
package grizzly

import org.glassfish.grizzly.http.server._
import org.glassfish.grizzly.threadpool.ThreadPoolConfig

/**
 * @author Bryce Anderson
 * @author ross
 */

object Example extends App {

  val http4sServlet = new Http4sGrizzly(ExampleRoute())

  val httpServer = new HttpServer
  val networkListener = new NetworkListener("sample-listener", "0.0.0.0", 8080)

  val threadPoolConfig = ThreadPoolConfig
    .defaultConfig()
    .setCorePoolSize(10)
    .setMaxPoolSize(100);

  networkListener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig);

  httpServer.addListener(networkListener);

  httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

    override def service(request: org.glassfish.grizzly.http.server.Request, response: Response) {
      response.setContentType("text/plain")
      response.getWriter().write("Simple Grizzly response!")
    }
  }, "/rawgrizzly")

  httpServer.getServerConfiguration().addHttpHandler(http4sServlet, "/grizzly/*")

  try {
    httpServer.start()
    //println("Press any key to stop the server...")
    Thread.currentThread().join()

  } catch  {
    case e: Throwable => println(e)
  }
}
