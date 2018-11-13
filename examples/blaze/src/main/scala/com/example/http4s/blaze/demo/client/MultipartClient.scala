package com.example.http4s.blaze.demo.client

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import com.example.http4s.blaze.demo.StreamUtils
import fs2.Stream
import java.net.URL
import org.http4s.{MediaType, Uri}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`
import org.http4s.Method._
import org.http4s.multipart.{Multipart, Part}
import scala.concurrent.ExecutionContext.Implicits.global

object MultipartClient extends MultipartHttpClient

class MultipartHttpClient(implicit S: StreamUtils[IO]) extends IOApp with Http4sClientDsl[IO] {

  private val image: IO[URL] = IO(getClass.getResource("/beerbottle.png"))

  private def multipart(url: URL) = Multipart[IO](
    Vector(
      Part.formData("name", "gvolpe"),
      Part.fileData("rick", url, global, `Content-Type`(MediaType.image.png))
    )
  )

  private val request =
    for {
      body <- image.map(multipart)
      req <- POST(body, Uri.uri("http://localhost:8080/v1/multipart"))
    } yield req.withHeaders(body.headers)

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      client <- BlazeClientBuilder[IO](global).stream
      req <- Stream.eval(request)
      value <- Stream.eval(client.expect[String](req))
      _ <- S.evalF(println(value))
    } yield ()).compile.drain.as(ExitCode.Success)
}
