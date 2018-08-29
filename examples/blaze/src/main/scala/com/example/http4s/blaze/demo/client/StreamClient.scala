package com.example.http4s.blaze.demo.client

import cats.effect.{ExitCode, IO, IOApp}
import com.example.http4s.blaze.demo.StreamUtils
import cats.implicits._
import io.circe.Json
import jawn.{RawFacade}
import org.http4s.client.blaze.Http1Client
import org.http4s.{Request, Uri}

object StreamClient extends HttpClient

class HttpClient(implicit S: StreamUtils[IO]) extends IOApp {
  implicit val jsonFacade: RawFacade[Json] = io.circe.jawn.CirceSupportParser.facade

  override def run(args: List[String]): IO[ExitCode] =
    Http1Client
      .stream[IO]()
      .flatMap { client =>
        val request = Request[IO](uri = Uri.uri("http://localhost:8080/v1/dirs?depth=3"))
        for {
          response <- client.streaming(request)(_.body.chunks.through(fs2.text.utf8DecodeC))
          _ <- S.putStr(response)
        } yield ()
      }
      .compile.drain.as(ExitCode.Success)

}
