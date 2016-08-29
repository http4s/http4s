package org.http4s.zipkin.examples

import java.time.Instant

import org.http4s.client.Client
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl._
import org.http4s.server.blaze._
import org.http4s.server.{Server, ServerApp}
import org.http4s.zipkin.algebras.{Clock, Randomness}
import org.http4s.zipkin.interpreters.clock.default
import org.http4s.zipkin.interpreters.collector.Http
import org.http4s.zipkin.interpreters.randomness.apply
import org.http4s.zipkin.middleware._
import org.http4s.zipkin.models.{Endpoint, ServerIds}
import org.http4s.{HttpService, Uri}

import scalaz.concurrent.Task
import scalaz.{Kleisli, Scalaz}

class SimpleRelayServerApp(
  getConfig: Task[Config],
  serviceDiscovery: String => Task[Uri],
  randomness: Randomness, clock: Clock
) extends ServerApp {

  val http1Client = PooledHttp1Client()
  val collectorInterpreter = Http(http1Client)

  val instrument: Client => ZipkinClient =
    ZipkinClient(collectorInterpreter, randomness, clock)

  val zipkinClient: ZipkinClient = instrument(http1Client)

  def serviceWithZipkinClient(nextServiceName: ServiceName)(
    serverRequirements: ServerRequirements
  ): HttpService = {

    HttpService {
      case req @ GET -> Root / "hello" / name =>
        for {
          nextServiceHost <- serviceDiscovery(nextServiceName)
          uri = nextServiceHost / s"api/hello/${name}"
          responseBody <- zipkinClient.map(_.expect[String](uri)).run(
            ClientRequirements(
              serverRequirements.serverIds, nextServiceName))
          result <- Ok(responseBody)
        } yield result
    }
  }

  def zipkinService(nextServiceName: ServiceName): ZipkinService =
    ZipkinServer.lift(serviceWithZipkinClient(nextServiceName))

  def serviceWithZipkin(me: Endpoint, nextServiceName: ServiceName): HttpService = {
    val lifted: ZipkinService =
      ZipkinServer.lift(serviceWithZipkinClient(nextServiceName))

    ZipkinServer(collectorInterpreter, randomness, clock, me)(lifted)
  }

  override def server(args: List[String]): Task[Server] = {
    for {
      config <- getConfig
      built <- BlazeBuilder
        .bindHttp(config.endpoint.port,config.endpoint.ipv4)
        .mountService(
          serviceWithZipkin(config.endpoint, config.nextServiceName), "/api")
        .start
    } yield built
  }
}
