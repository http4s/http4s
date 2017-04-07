package com.example.http4s
package jetty

import javax.servlet._

import com.codahale.metrics.MetricRegistry
import org.http4s.server.ServerApp
import org.http4s.server.jetty.JettyConfig
import org.http4s.server.metrics._

object JettyExample extends ServerApp {
  val metrics = new MetricRegistry

  import javax.servlet.http._
  val foo = new HttpServlet {
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = {
      val ctx = req.startAsync()
      ctx.start(new Runnable {
        def run() = {
          ctx.complete()
        }
      })
    }
  }

  def server(args: List[String]) = JettyConfig.default
    .bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .mountService(metricsService(metrics), "/metrics")
    .mountServlet(foo, "/foo/*")
    .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
    .start
}
