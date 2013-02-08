package org.http4s.test

import org.http4s._

import play.api.libs.iteratee.{Concurrent, Done}
import org.glassfish.grizzly.http.server._
import org.glassfish.grizzly.threadpool.ThreadPoolConfig

import org.http4s.grizzly.Http4sGrizzly
import org.http4s.Responder

import org.http4s.Writable._
import org.http4s.Bodies._


/**
 * @author ross
 */

/*
  The idea is that you have a partial function that gets called to return an iteratee.
  That iteratee returns Responder which contains an Enumerator in the body field that will
  be used to fulfill the rest of the request.

  The problem is that the body is pushed into the first iteratee, which mucks up the
  Concurrent.unicast which is clearly the right Enumerator to use for dealing wtih the body
  of the request.

 */
object Example extends App {
  val http4sServlet = new Http4sGrizzly({
    case req if req.pathInfo == "/ping" =>
      Done(Responder(body = "pong"))

    case req if req.pathInfo == "/stream" =>
      Done(Responder(body = Concurrent.unicast({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/echo" =>
      println("Doing Echo")
      Done(Responder(body = req.body))

    case req =>
      println(s"Request path: ${req.pathInfo}")
      Done(Responder(body =
        s"${req.pathInfo}\n" +
        s"${req.uri}"
      ))
  })

  val httpServer = new HttpServer
  val networkListener = new NetworkListener("sample-listener", "brycepc.dyndns.org", 8080)

  // Configure NetworkListener thread pool to have just one thread,
  // so it would be easier to reproduce the problem
  val threadPoolConfig = ThreadPoolConfig
    .defaultConfig()
    .setCorePoolSize(1)
    .setMaxPoolSize(1);

  networkListener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig);

  httpServer.addListener(networkListener);

  httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

    override def service(request: org.glassfish.grizzly.http.server.Request, response: Response) {
      response.setContentType("text/plain")
      response.getWriter().write("Simple task is done!")
    }
  }, "/simple")

  httpServer.getServerConfiguration().addHttpHandler(http4sServlet, "/grizzly/*")

  try {
    httpServer.start()
    println("Press any key to stop the server...")
    readLine
    Thread.currentThread().join()

  } catch  {
    case e: Throwable => println(e)
  }
}
