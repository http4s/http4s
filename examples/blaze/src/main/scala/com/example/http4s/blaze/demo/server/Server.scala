package com.example.http4s.blaze.demo.server

import cats.effect._
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import org.http4s.client.blaze.Http1Client
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext.Implicits.global

object Server extends HttpServer[IO]

class HttpServer[F[_]](implicit F: ConcurrentEffect[F]) extends StreamApp[F] {

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] = {
    implicit val T = Timer.derive[F]
    for {
      client <- Http1Client.stream[F]()
      ctx <- Stream(new Module[F](client))
      exitCode <- BlazeBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .mountService(ctx.fileHttpEndpoint, s"/${endpoints.ApiVersion}")
        .mountService(ctx.nonStreamFileHttpEndpoint, s"/${endpoints.ApiVersion}/nonstream")
        .mountService(ctx.httpServices)
        .mountService(ctx.basicAuthHttpEndpoint, s"/${endpoints.ApiVersion}/protected")
        .serve
    } yield exitCode
  }

}
