package com.example.http4s.blaze

import java.util.concurrent._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl._
import org.http4s.server._
import org.http4s.server.blaze._
import org.http4s.client.blaze._
import org.http4s.util.managed._
import scalaz._
import scalaz.concurrent._

/** How to manage the lifecycle of a server and its dependent resources */
object ManagedExample extends ManagedServerApp {
  // This service depends on a client.
  def service(client: Client): HttpService = HttpService {
    case GET -> Root / "proxy" =>
      client.toHttpService.run(Request(Method.GET, uri("http://http4s.org/")))
  }

  def runServer(args: Vector[String]) = for {
    // These resources will be closed in reverse order
    clientExecutor <- manage(Executors.newFixedThreadPool(10))
    client <- manage(PooledHttp1Client(config = BlazeClientConfig.defaultConfig(clientExecutor)))
    serverExecutor <- manage(Executors.newFixedThreadPool(10))
    server <- BlazeBuilder.withServiceExecutor(serverExecutor).mountService(service(client)).managed
  } yield server
}
