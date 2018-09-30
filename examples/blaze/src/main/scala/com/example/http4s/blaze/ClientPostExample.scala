package com.example.http4s.blaze

import cats.effect._
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import scala.concurrent.ExecutionContext.Implicits.global

object ClientPostExample extends IOApp with Http4sClientDsl[IO] {
  def run(args: List[String]): IO[ExitCode] = {
    val req = POST(uri("https://duckduckgo.com/"), UrlForm("q" -> "http4s"))
    val responseBody = BlazeClientBuilder[IO](global).resource.use(_.expect[String](req))
    responseBody.flatMap(resp => IO(println(resp))).map(_ => ExitCode.Success)
  }
}
