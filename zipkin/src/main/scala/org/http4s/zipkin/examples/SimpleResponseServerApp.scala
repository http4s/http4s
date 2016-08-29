package org.http4s.zipkin.examples

import org.http4s._
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerApp}
import org.http4s.zipkin.algebras.{Clock, Randomness}
import org.http4s.zipkin.interpreters.collector.Http
import org.http4s.zipkin.middleware.{ZipkinServer, _}
import org.http4s.zipkin.models.Endpoint

import scalaz.concurrent.Task

class SimpleResponseServerApp(
  getConfig: Task[Endpoint],
  randomness: Randomness, clock: Clock
) extends ServerApp {

  val originalService: HttpService = HttpService {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }
  val lifted: ZipkinService =
    ZipkinServer.liftVanilla(originalService)

  def instrument(me: Endpoint): ZipkinService => HttpService =
    ZipkinServer(
      new Http(PooledHttp1Client()), randomness, clock, me)

  override def server(args: List[String]): Task[Server] = {
    for {
      config <- getConfig
      built <- BlazeBuilder
        .bindHttp(config.port, config.ipv4)
        .mountService(instrument(config)(lifted), "/api")
        .start
    } yield built
  }
}
