package com.example.http4s.zipkin

import org.http4s.client.Client
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl._
import org.http4s.server.blaze._
import org.http4s.server.{Server, ServerApp}
import org.http4s.zipkin.client.{ClientRequirements, ZipkinClient}
import org.http4s.zipkin.core.algebras.{Clock, Randomness}
import org.http4s.zipkin.core.interpreters.collector.Http
import org.http4s.zipkin.core.Endpoint
import org.http4s.zipkin.middleware.server.ZipkinService
import org.http4s.zipkin.server.{ServerRequirements, ZipkinServer}
import org.http4s.{HttpService, Uri}

import scalaz.concurrent.Task

class SimpleRelayServerApp(
  getConfig: Task[Config],
  serviceDiscovery: String => Task[Uri],
  randomness: Randomness, clock: Clock
) extends ServerApp {

  val client = PooledHttp1Client()
  val collector = Http(client)

  val zipkinClient =
    ZipkinClient(collector, randomness, clock)(client)

  def serviceWithZipkinClient(nextServiceName: ServiceName)(
    serverRequirements: ServerRequirements
  ): HttpService = {
    val client = zipkinClient.run(
      ClientRequirements(serverRequirements.serverIds, nextServiceName))

    HttpService {
      case req @ GET -> Root / "hello" / name =>
        for {
          nextServiceHost <- serviceDiscovery(nextServiceName)
          uri = nextServiceHost / s"api/hello/${name}"
          responseBody <- client.expect[String](uri)
          result <- Ok(responseBody)
        } yield result
    }
  }

  def serviceWithZipkinServer(me: Endpoint, nextServiceName: ServiceName): HttpService = {
    val zipkinService =
      ZipkinServer.lift(serviceWithZipkinClient(nextServiceName))

    ZipkinServer(collector, randomness, clock, me)(zipkinService)
  }

  override def server(args: List[String]): Task[Server] = {
    for {
      config <- getConfig
      built <- BlazeBuilder
        .bindHttp(config.endpoint.port,config.endpoint.ipv4)
        .mountService(
          serviceWithZipkinServer(config.endpoint, config.nextServiceName), "/api")
        .start
    } yield built
  }


  override def shutdown(server: Server): Task[Unit] = for {
    _ <- server.shutdown
    _ <- client.shutdown
  } yield ()
}
