package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import scala.concurrent.ExecutionContext.Implicits.global

object ClientPostExample extends IOApp with Http4sClientDsl[IO] {
  def run(args: List[String]): IO[ExitCode] = {
    val req = POST(UrlForm("q" -> "http4s"), uri("https://duckduckgo.com/"))
    val responseBody = BlazeClientBuilder[IO](global).resource.use(_.expect[String](req))
    responseBody.flatMap(resp => IO(println(resp))).as(ExitCode.Success)
  }
}
