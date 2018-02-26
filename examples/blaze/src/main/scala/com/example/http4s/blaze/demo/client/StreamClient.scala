package com.example.http4s.blaze.demo.client

import cats.effect.{Effect, IO}
import com.example.http4s.blaze.demo.StreamUtils
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import io.circe.Json
import jawn.Facade
import org.http4s.client.blaze.Http1Client
import org.http4s.{Request, Uri}

object StreamClient extends HttpClient[IO]

class HttpClient[F[_]](implicit F: Effect[F], S: StreamUtils[F]) extends StreamApp {

  implicit val jsonFacade: Facade[Json] = io.circe.jawn.CirceSupportParser.facade

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    Http1Client
      .stream[F]()
      .flatMap { client =>
        val request = Request[F](uri = Uri.uri("http://localhost:8080/v1/dirs?depth=3"))
        for {
          response <- client.streaming(request)(_.body.chunks.through(fs2.text.utf8DecodeC))
          _ <- S.putStr(response)
        } yield ()
      }
      .drain

}
