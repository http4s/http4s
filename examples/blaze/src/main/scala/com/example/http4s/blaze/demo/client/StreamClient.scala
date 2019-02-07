package com.example.http4s.blaze.demo.client

import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp}
import com.example.http4s.blaze.demo.StreamUtils
import cats.implicits._
import io.circe.Json
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{Request, Uri}
import org.typelevel.jawn.RawFacade
import scala.concurrent.ExecutionContext.Implicits.global

object StreamClient extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    new HttpClient[IO].run.as(ExitCode.Success)
}

class HttpClient[F[_]](implicit F: ConcurrentEffect[F], S: StreamUtils[F]) {
  implicit val jsonFacade: RawFacade[Json] = io.circe.jawn.CirceSupportParser.facade

  def run: F[Unit] =
    BlazeClientBuilder[F](global).stream
      .flatMap { client =>
        val request = Request[F](uri = Uri.uri("http://localhost:8080/v1/dirs?depth=3"))
        for {
          response <- client.stream(request).flatMap(_.body.chunks.through(fs2.text.utf8DecodeC))
          _ <- S.putStr(response)
        } yield ()
      }
      .compile
      .drain
}
