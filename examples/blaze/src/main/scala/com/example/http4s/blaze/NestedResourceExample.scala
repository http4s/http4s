package com.example.http4s.blaze

import java.util.concurrent._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl._
import org.http4s.server._
import org.http4s.server.blaze._
import org.http4s.client.blaze._
import org.http4s.util.ProcessApp
import scalaz._
import scalaz.concurrent._
import scalaz.stream._, Process._

/** How to manage the lifecycle of a server and its dependent resources */
object NestedResourceExample extends ProcessApp {
  // This service depends on a client.
  def service(client: Client): HttpService = HttpService {
    case GET -> Root / "proxy" =>
      client.toHttpService.run(Request(Method.GET, uri("http://http4s.org/")))
  }

  def main(args: List[String]) =
    bracket(Task.delay(Executors.newFixedThreadPool(10)))(e => eval_(Task.delay(e.shutdown))) { clientExecutor =>
      bracket(Task.delay(PooledHttp1Client(config = BlazeClientConfig.defaultConfig.copy(customExecutor = Some(clientExecutor)))))(c => eval_(c.shutdown)) { client =>
        bracket(Task.delay(Executors.newFixedThreadPool(10)))(e => eval_(Task.delay(e.shutdown))) { serverExecutor =>
          BlazeBuilder.withServiceExecutor(serverExecutor).mountService(service(client)).process
        }
      }
    }
}
