package com.example.http4s
package blaze

import cats.effect._
import cats.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext.global

object BlazeSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslExampleApp.builder[IO].serve.compile.drain.as(ExitCode.Success)
}

object BlazeSslExampleApp {

  def builder[F[_]: ConcurrentEffect: ContextShift: Timer]: BlazeServerBuilder[F] =
    BlazeServerBuilder[F](global)
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)
      .withHttpApp(BlazeExampleApp.httpApp[F])

}
