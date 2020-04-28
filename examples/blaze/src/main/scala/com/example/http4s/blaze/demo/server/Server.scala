package com.example.http4s.blaze.demo.server

import cats.effect._
import fs2.Stream
import org.http4s.HttpApp
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scala.concurrent.ExecutionContext.global

object Server extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    HttpServer.stream[IO].compile.drain.as(ExitCode.Success)
}

object HttpServer {
  def httpApp[F[_]: Sync](ctx: Module[F]): HttpApp[F] =
    Router(
      s"/${endpoints.ApiVersion}/protected" -> ctx.basicAuthHttpEndpoint,
      s"/${endpoints.ApiVersion}" -> ctx.fileHttpEndpoint,
      s"/${endpoints.ApiVersion}/nonstream" -> ctx.nonStreamFileHttpEndpoint,
      "/" -> ctx.httpServices
    ).orNotFound

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] =
    for {
      blocker <- Stream.resource(Blocker[F])
      client <- BlazeClientBuilder[F](global).stream
      ctx <- Stream(new Module[F](client, blocker))
      exitCode <- BlazeServerBuilder[F](global)
        .bindHttp(8080)
        .withHttpApp(httpApp(ctx))
        .serve
    } yield exitCode
}
